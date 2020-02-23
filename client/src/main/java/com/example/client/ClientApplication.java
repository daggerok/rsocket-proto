package com.example.client;

import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.rpc.testing.protobuf.SimpleRequest;
import io.rsocket.rpc.testing.protobuf.SimpleResponse;
import io.rsocket.rpc.testing.protobuf.SimpleServiceClient;
import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Log4j2
@SpringBootApplication
public class ClientApplication {

  public static void main(String[] args) {

    RSocket rSocket = RSocketFactory.connect()
                                    .transport(TcpClientTransport.create(7070))
                                    .start()
                                    .block();

    SimpleServiceClient client = new SimpleServiceClient(rSocket);

    Flux<SimpleRequest> requests =
        Flux.range(1, 11)
            .map(i -> "sending -> " + i)
            .map(s -> SimpleRequest.newBuilder()
                                   .setRequestMessage(s)
                                   .build());

    Mono<SimpleResponse> response = client.streamingRequestSingleResponse(requests);
    Mono<String> message = response.map(SimpleResponse::getResponseMessage);
    message.doOnSubscribe(subscription -> log.info("subscribing..."))
           .doOnEach(each -> log.info("on each: {}", each))
           .doOnNext(next -> log.info("next: {}", next))
           .doOnCancel(() -> log.info("cancel..."))
           .subscribe(log::info);

    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException e) {
      log.error("oops... {}", e.getLocalizedMessage(), e);
    }

    log.info("Done.");

    // SpringApplication.run(ClientApplication.class, args);
  }
}
