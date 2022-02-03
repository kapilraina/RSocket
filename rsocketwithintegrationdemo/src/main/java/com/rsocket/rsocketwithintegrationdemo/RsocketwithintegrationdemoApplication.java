package com.rsocket.rsocketwithintegrationdemo;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.transformer.FileToStringTransformer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.integration.handler.GenericHandler;
import org.springframework.integration.rsocket.ClientRSocketConnector;
import org.springframework.integration.rsocket.RSocketInteractionModel;
import org.springframework.integration.rsocket.dsl.RSockets;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.rsocket.RSocketStrategies;

@SpringBootApplication
public class RsocketwithintegrationdemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(RsocketwithintegrationdemoApplication.class, args);
	}

	@Bean
	ClientRSocketConnector clientRSocketConnector(RSocketStrategies strategies)
	{
		var crc =  new ClientRSocketConnector("localhost", 8888);
		crc.setRSocketStrategies(strategies);
		return crc;
	}
	@Bean
	IntegrationFlow incomingFileFlow(@Value("file:///${user.home}/temp") File f, ClientRSocketConnector clientRSocketConnector) {
		var fileReadingMessageSources = Files
				.inboundAdapter(f)
				.autoCreateDirectory(true)
				.get();

		var rsocketOutBoundGateway = RSockets.outboundGateway("greetings")
				.interactionModel(RSocketInteractionModel.requestStream)
				.clientRSocketConnector(clientRSocketConnector)
				.expectedResponseType(GreetingResponse.class);

		return IntegrationFlows
				.from(fileReadingMessageSources, pspec -> pspec.poller(pm -> pm.fixedDelay(1000)))
				.transform((GenericTransformer<File, String>) source -> source.getAbsolutePath())
				.transform(String.class, name -> new GreetingRequest(name.trim()))
				.handle(rsocketOutBoundGateway)
				.split()
				.channel(MessageChannels.flux().get())
				.handle(new GenericHandler<GreetingResponse>() {
					@Override
					public Object handle(GreetingResponse greetingResponse, MessageHeaders headers) {
						System.out.println("Incoming :: "+greetingResponse);
						return null;
					}
				})
				.get();

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