package com.rsocket.rsocketclientdemo;

import io.rsocket.SocketAcceptor;
import io.rsocket.metadata.WellKnownMimeType;
import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class RsocketclientdemoApplication {

  @SneakyThrows
  public static void main(String[] args) {
    SpringApplication.run(RsocketclientdemoApplication.class, args);
    System.in.read();
  }

  final UsernamePasswordMetadata creds = new UsernamePasswordMetadata(
    "neo",
    "password"
  );

  final MimeType credMimeType = MimeTypeUtils.parseMimeType(
    WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.toString()
  );

  @Bean
  SocketAcceptor socketAcceptor(
    RSocketStrategies strategies,
    HealtController controller
  ) {
    return RSocketMessageHandler.responder(strategies, controller);
    //return RSocketMessageHandler.responder(strategies);
  }

  @Bean
  RSocketRequester rSocketRequester(
    RSocketRequester.Builder builder,
    SocketAcceptor socketAcceptor
  ) {
    return builder
      .setupMetadata(this.creds, this.credMimeType)
      .rsocketConnector(configurer -> configurer.acceptor(socketAcceptor))
      .tcp("localhost", 8888);
  }

  @Bean
  RSocketStrategiesCustomizer rSocketStrategiesCustomizer() {
    return strategies -> strategies.encoder(new SimpleAuthenticationEncoder());
  }

  @Bean
  ApplicationListener<ApplicationReadyEvent> client(
    RSocketRequester requester
  ) {
    return args -> {
      Flux<GreetingResponse> retrieveFlux = requester
        .route("greetings")
        //.metadata(this.creds, this.credMimeType); //set in requester setup
        // .data(new GreetingRequest("Ruhaaan"))
        .retrieveFlux(GreetingResponse.class);
      retrieveFlux.subscribe(System.out::println);
    };
  }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class ClientHealthSnap {

  private boolean isHealthy;
}

@Controller
class HealtController {

  @MessageMapping("health")
  Flux<ClientHealthSnap> checkHealth() {
    var healthStream = Stream.generate(
      () -> {
        int rn = (new Random()).nextInt(100);
        System.out.println("rn = " + rn);
        return new ClientHealthSnap(rn % 2 == 0);
      }
    );
    return Flux.fromStream(healthStream).delayElements(Duration.ofSeconds(1));
  }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class GreetingRequest {

  String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class GreetingResponse {

  String message;
}
