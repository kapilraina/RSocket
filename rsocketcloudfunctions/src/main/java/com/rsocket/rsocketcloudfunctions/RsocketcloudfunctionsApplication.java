package com.rsocket.rsocketcloudfunctions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import lombok.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;

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


