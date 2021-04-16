package com.example.server;

import com.google.protobuf.Empty;
import io.netty.buffer.ByteBuf;
import io.rsocket.RSocketFactory;
import io.rsocket.rpc.rsocket.RequestHandlingRSocket;
import io.rsocket.rpc.testing.protobuf.SimpleRequest;
import io.rsocket.rpc.testing.protobuf.SimpleResponse;
import io.rsocket.rpc.testing.protobuf.SimpleService;
import io.rsocket.rpc.testing.protobuf.SimpleServiceServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Log4j2
class DefaultSimpleService implements SimpleService {

  @Override
  public Mono<Empty> fireAndForget(SimpleRequest message, ByteBuf metadata) {
    log.info("got fireAndForget message -> {}", message.getRequestMessage());
    return Mono.just(Empty.getDefaultInstance());
  }

  @Override
  public Mono<SimpleResponse> requestReply(SimpleRequest message, ByteBuf metadata) {
    String msg = message.getRequestMessage();
    log.info("requestReply: -> {}", msg);
    SimpleResponse response = SimpleResponse.newBuilder()
                                            .setResponseMessage("we got requestReply message -> " + msg)
                                            .build();
    return Mono.just(response);
  }

  @Override
  public Mono<SimpleResponse> streamingRequestSingleResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
    log.info("streamingRequestSingleResponse ->");
    return Flux.from(messages)
               .windowTimeout(10, Duration.ofSeconds(500))
               .take(1)
               .flatMap(Function.identity())
               .reduce(new ConcurrentHashMap<Character, AtomicInteger>(), (map, simpleRequest) -> {
                 char[] chars = simpleRequest.getRequestMessage()
                                             .toCharArray();
                 for (char ch : chars)
                   map.computeIfAbsent(ch, character -> new AtomicInteger())
                      .incrementAndGet();
                 return map;
               })
               .map(map -> {
                 StringBuilder builder = new StringBuilder();
                 map.forEach((character, atomicInteger) -> builder.append(
                     String.format("character -> %s, count -> %d%n",
                                   character, atomicInteger.get()))
                 );
                 map.forEach((character, atomicInteger) -> log
                     .info("character -> {}, count -> {}", character, atomicInteger.get()));
                 String s = builder.toString();
                 return SimpleResponse.newBuilder().setResponseMessage(s).build();
               });
  }

  @Override
  public Flux<SimpleResponse> requestStream(SimpleRequest message, ByteBuf metadata) {
    String requestMessage = message.getRequestMessage();
    log.info("requestStream -> {}", requestMessage);
    return Flux.interval(Duration.ofMillis(200))
               .onBackpressureDrop()
               .map(i -> i + " - got message - " + requestMessage)
               .map(s -> SimpleResponse.newBuilder().setResponseMessage(s).build());
  }

  @Override
  public Flux<SimpleResponse> streamingRequestAndResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
    return Flux.from(messages).flatMap(e -> requestReply(e, metadata));
  }
}

@Configuration
class RSocketConfig {

  @Bean
  SimpleServiceServer serviceServer() {
    return new SimpleServiceServer(new DefaultSimpleService(), Optional.empty(), Optional.empty());
  }

  @Bean
  RSocketFactory.Start<CloseableChannel> starter(SimpleServiceServer serviceServer) {
    return RSocketFactory.receive()
                         .acceptor((setup, sendingSocket) -> Mono.just(new RequestHandlingRSocket(serviceServer)))
                         .transport(TcpServerTransport.create(7070));
  }

  // @Bean // in general should be something like so, but ...we are moving it in main static method...
  ApplicationRunner startAndWait(RSocketFactory.Start<CloseableChannel> starter) {
    return args -> starter.start()
                          .block()
                          .onClose()
                          .block();
  }
}

@SpringBootApplication
public class ServerApplication {
  public static void main(String[] args) {
    RSocketFactory.Start<CloseableChannel> starter = SpringApplication.run(ServerApplication.class, args)
                                                                      .getBean(RSocketFactory.Start.class);
    starter.start()
           .block()
           .onClose()
           .block();
  }
}
