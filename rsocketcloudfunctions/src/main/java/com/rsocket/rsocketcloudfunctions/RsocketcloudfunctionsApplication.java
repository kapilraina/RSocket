package com.rsocket.rsocketcloudfunctions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.*;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.Message;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class RsocketcloudfunctionsApplication {

	@SneakyThrows
	public static void main(String[] args) {
		SpringApplication.run(RsocketcloudfunctionsApplication.class, args);

	}

	// Request - Stream
	@Bean
	public Function<String, Flux<String>> greetings()
	{
		return name -> Flux.fromStream(
				Stream.generate(() -> "Hello | Nihao | Namaste" + name + " ! @ " + Instant.now())
		).delayElements(Duration.ofSeconds(1));
	}

	@Bean
	public Function<String,Flux<GreetingResponse>> greetingmessageflux()
	{
		return name ->
				Flux.fromStream(Stream.generate(()->{
					return new GreetingResponse(UUID.randomUUID().toString(), name, Instant.now().toString());
				})).delayElements(Duration.ofSeconds(2));
	}

	//Request - Response
	@Bean
	public Function<String, String> greeting()
	{
		return name -> "Hello " + name + " ! @ " + Instant.now();
	}

	@Bean
	public Function<Message<String>, Message<String>> greetingmessage()
	{
		return message -> {
			String responsePayload = message.getPayload() + " | " + message.getHeaders();
			Message<String> responseMessage = MessageBuilder.withPayload(responsePayload).setHeader("x-state","state-of-mind").build();
			return responseMessage;
		};
	}

	// Fire-and-Forget
	@Bean
	public Consumer<String> acceptGreeting()
	{
		return s -> System.out.println("Thanks you for your greetings : " + s);

	}

	@Bean
	public Function<Mono<Void>,Flux<GreetingResponse>> securegreetings()
	{
		return v ->
		{

			return ReactiveSecurityContextHolder.getContext()
					.map(sc -> ((UserDetails)sc.getAuthentication().getPrincipal()).getUsername())
					.flatMapMany(
							username -> Flux.fromStream(Stream.generate(()->{

								return new GreetingResponse(UUID.randomUUID().toString(),
										"Hello " + username,
										Instant.now().toString());

							}))).delayElements(Duration.ofSeconds(2));

		};

	}



    @Bean
    public Function<RSocketRequester, Flux<GreetingResponse>> securegreetingswithHealthcheck()
    {
        return (requester) ->
        {

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

			Flux<GreetingResponse> greetingResponseFlux = ReactiveSecurityContextHolder.getContext()
					.map(sc -> ((UserDetails) sc.getAuthentication().getPrincipal()).getUsername())
					.flatMapMany(
							username -> Flux.fromStream(Stream.generate(() -> {

								return new GreetingResponse(UUID.randomUUID().toString(),
										"Hello " + username,
										Instant.now().toString());

							})));//.delayElements(Duration.ofSeconds(1));

			return greetingResponseFlux.takeUntilOther(healthFlux);

		};

    }

}


@Configuration
@EnableRSocketSecurity
@EnableReactiveMethodSecurity
class SecurityConfiguration {
	@Bean
	MapReactiveUserDetailsService authentication() {
		UserDetails defaultUser =
				User
						.withDefaultPasswordEncoder()
						.password("password")
						.username("trinity")
						.roles("ADMIN", "USER")
						.build();
		MapReactiveUserDetailsService uds = new MapReactiveUserDetailsService(defaultUser);
		return uds;
	}

	@Bean
	PayloadSocketAcceptorInterceptor authorization(RSocketSecurity rsecurity) {
		return rsecurity
				.authorizePayload(ap -> ap.anyExchange().authenticated())
				.simpleAuthentication(Customizer.withDefaults())
				.build();

	}

	@Bean
	RSocketMessageHandler rSocketMessageHandler(RSocketStrategies strategies) {
		var rsmh = new RSocketMessageHandler();
		rsmh.getArgumentResolverConfigurer().addCustomResolver(new AuthenticationPrincipalArgumentResolver());
		rsmh.setRSocketStrategies(strategies);
		return rsmh;
	}



}

//@Configuration
class TransportConfigs
{
	@Bean
	public RSocketStrategiesCustomizer tRSocketStrategyCustomizer() {
		return (strategy) -> {
			strategy.decoder(new Jackson2JsonDecoder());
			strategy.encoder(new Jackson2JsonEncoder());
		};
	}
}


@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class GreetingResponse
{
	private String id;
	private String message;
	private String timestamp;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
class ClientHealthSnap {

	private boolean isHealthy;
}


