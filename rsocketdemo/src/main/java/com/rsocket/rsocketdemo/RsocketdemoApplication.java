package com.rsocket.rsocketdemo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.UserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class RsocketdemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(RsocketdemoApplication.class, args);
  }
}

@Controller
class GreetingContoller {

  @MessageMapping("greetings")
  Flux<GreetingResponse> greetings(
    //GreetingRequest request,
    RSocketRequester requester, @AuthenticationPrincipal Mono<UserDetails> user
  ) {

    var healthFlux = requester
      .route("health")
      .retrieveFlux(ClientHealthSnap.class)
      .map(
        chs -> {
          System.out.println(chs);
          return chs;
        }
      )
      .filter(h -> !h.isHealthy());

      var out = user.map(userDetails -> userDetails.getUsername())
              .flatMapMany(username -> Flux
                      .fromStream(
                              Stream.generate(
                                      () ->
                                              new GreetingResponse(
                                                      "Hello " + username + " @ " + Instant.now()
                                              )
                              )
                      )
                      .take(100)
                      .delayElements(Duration.ofSeconds(1)));


    /*var out = Flux
      .fromStream(
        Stream.generate(
          () ->
            new GreetingResponse(
              "Hello " + request.getName() + " @ " + Instant.now()
            )
        )
      )
      .take(100)
      .delayElements(Duration.ofSeconds(1));*/

    // return Flux.fromStream(stream).take(20).delayElements(Duration.ofSeconds(1));
    return out.takeUntilOther(healthFlux);
  }
}


@Configuration
class SecurityConfiguration 
{
	@Bean
    MapReactiveUserDetailsService uds()
	{
        UserDetails defaultUser =
                User
                .withDefaultPasswordEncoder()
                .password("password")
                .username("neo")
                .roles("ADMIN","USER")
                .build();
        MapReactiveUserDetailsService uds = new MapReactiveUserDetailsService(defaultUser);
		return uds;
	}

	@Bean
	PayloadSocketAcceptorInterceptor authorization(RSocketSecurity rsecurity)
    {
        return rsecurity
                .authorizePayload(ap -> ap.anyExchange().authenticated())
                .simpleAuthentication(Customizer.withDefaults())
                .build();

    }

    @Bean
    RSocketMessageHandler rSocketMessageHandler(RSocketStrategies strategies)
    {
        var rsmh = new RSocketMessageHandler();
        rsmh.getArgumentResolverConfigurer().addCustomResolver(new AuthenticationPrincipalArgumentResolver());
        rsmh.setRSocketStrategies(strategies);
        return rsmh;
    }
}




@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class ClientHealthSnap {

  private boolean isHealthy;
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
