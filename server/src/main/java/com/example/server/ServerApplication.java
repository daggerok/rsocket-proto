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
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class DefaultSimpleService implements SimpleService {
  @Override
  public Mono<Empty> fireAndForget(SimpleRequest message, ByteBuf metadata) {
    System.out.println("got fireAndForget message -> " + message.getRequestMessage());
    return Mono.just(Empty.getDefaultInstance());
  }

  @Override
  public Mono<SimpleResponse> requestReply(SimpleRequest message, ByteBuf metadata) {
    return Mono.fromCallable(
        () ->
            SimpleResponse.newBuilder()
                          .setResponseMessage("we got requestReply message -> " + message.getRequestMessage())
                          .build());
  }

  @Override
  public Mono<SimpleResponse> streamingRequestSingleResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
    return Flux.from(messages)
               .windowTimeout(10, Duration.ofSeconds(500))
               .take(1)
               .flatMap(Function.identity())
               .reduce(
                   new ConcurrentHashMap<Character, AtomicInteger>(),
                   (map, s) -> {
                     char[] chars = s.getRequestMessage().toCharArray();
                     for (char c : chars) {
                       map.computeIfAbsent(c, _c -> new AtomicInteger()).incrementAndGet();
                     }
                     return map;
                   })
               .map(
                   map -> {
                     StringBuilder builder = new StringBuilder();
                     map.forEach(
                         (character, atomicInteger) -> builder.append(
                             String.format("character -> %s, count -> %d%n",
                                           character, atomicInteger.get())));

                     String s = builder.toString();
                     return SimpleResponse.newBuilder().setResponseMessage(s).build();
                   });
  }

  @Override
  public Flux<SimpleResponse> requestStream(SimpleRequest message, ByteBuf metadata) {
    String requestMessage = message.getRequestMessage();
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

@SpringBootApplication
public class ServerApplication {

  public static void main(String[] args) {
    SimpleServiceServer serviceServer = new SimpleServiceServer(new DefaultSimpleService(), Optional.empty(), Optional.empty());
    CloseableChannel closeableChannel =
        RSocketFactory.receive()
                      .acceptor(
                          (setup, sendingSocket) -> Mono.just(new RequestHandlingRSocket(serviceServer)))
                      .transport(TcpServerTransport.create(7070))
                      .start()
                      .block();

    // Block so we don't exit
    closeableChannel.onClose().block();

    ConfigurableApplicationContext context = SpringApplication.run(ServerApplication.class, args);
    context.close();
  }
}
