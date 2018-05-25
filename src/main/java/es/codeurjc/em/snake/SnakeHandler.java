package es.codeurjc.em.snake;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnakeHandler extends TextWebSocketHandler {

	private static final String SNAKE_ATT = "snake";
	private static final String ROOM_ATT = "room";
	private static final String PLAYING_ATT = "isPlaying";

	// Nuevas variables
	private Map<String, SnakeGame> lobby = new ConcurrentHashMap<String, SnakeGame>();
	private Map<String, Snake> snakesLobby = new ConcurrentHashMap<String, Snake>();
	private AtomicInteger lobbyIds = new AtomicInteger(0);
	private ObjectMapper mapper = new ObjectMapper();
	//

	private AtomicInteger snakeIds = new AtomicInteger(0);
	

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {

		int id = snakeIds.getAndIncrement();
		Snake s = new Snake(id, session);
		session.getAttributes().put(SNAKE_ATT, s);
		session.getAttributes().put(PLAYING_ATT, false);
		snakesLobby.put(session.getId(), s);

		updateRooms();

	}

	public void updateRooms() throws Exception {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, SnakeGame> entry : lobby.entrySet()) {
			String key = entry.getKey().toString();
			sb.append(String.format("{\"id\": \"%s\"}", key));
			sb.append(',');
		}
		System.out.println(sb.toString());
		if (sb.length() != 0) {
			sb.deleteCharAt(sb.length() - 1);
			String msg = String.format("{\"type\": \"updateRooms\",\"data\":[%s]}", sb.toString());
			for (Map.Entry<String, Snake> entry : snakesLobby.entrySet()) {
				entry.getValue().sendMessage(msg);
			}
		}
	}

	public void createRoom(WebSocketSession session, String payload) throws Exception {
		boolean isPlaying = (boolean) session.getAttributes().get(PLAYING_ATT);
		if (!isPlaying) {
			if(!lobby.containsKey(payload)) {
				SnakeGame sg = new SnakeGame(payload, session); // Se crea nuevo juego
				lobby.put(payload, sg); // Se almacena en una ED
				Snake s = snakesLobby.remove(session.getId()); // Se crea la serpiente del usuario seleccionándola de la ED
				lobbyIds.getAndIncrement(); // Se aumenta el ID de salas
				sg.addSnake(s); // Se añade la serpiente al juego
				session.getAttributes().put(PLAYING_ATT, true); // la sesión asociada a dicha serpiente, ahora está jugando
																// la próxima vez que intente meterse en una sala, no podrá
				session.getAttributes().put(ROOM_ATT, sg);

				StringBuilder sb = new StringBuilder();
				for (Snake snake : sg.getSnakes()) {
					sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
					sb.append(',');
				}
				sb.deleteCharAt(sb.length() - 1);
				String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());
				System.out.println(msg);
				sg.broadcast(msg);

				updateRooms();
			}
			else {
				System.out.println("Ya existe una sala con ese nombre");
			}

		} else {
			System.out.println("Ya estás en la sala");
		}

	}

	public void controlMovement(WebSocketSession session, String payload) {

		Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);

		Direction d = Direction.valueOf(payload.toUpperCase());
		s.setDirection(d);
	}

	public void joinRoom(WebSocketSession session, String idRoom) throws Exception {
		SnakeGame sg = lobby.get(idRoom);
		if(sg.getSnakes().size() < 4) {
			int idSnake = snakeIds.getAndIncrement();
			Snake s = new Snake(idSnake, session);
			sg.addSnake(s);
			session.getAttributes().put(ROOM_ATT, sg);
			StringBuilder sb = new StringBuilder();
			for (Snake snake : sg.getSnakes()) {
				sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
				sb.append(',');
			}
			sb.deleteCharAt(sb.length() - 1);
			String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());
			System.out.println(msg);
			sg.broadcast(msg);
		}else {
			System.out.println("Demasiadas serpientes");
		}
		
	}
	
	public void joinRandom(WebSocketSession session) throws Exception {
		String key = "";
		int aux = 5;
		for (Map.Entry<String, SnakeGame> entry : lobby.entrySet()) {
			if(entry.getValue().getSnakes().size() < aux) {
				aux = entry.getValue().getSnakes().size();
				key = entry.getKey();
			}
		}
		joinRoom(session, key);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		try {

			JsonNode node = mapper.readTree(message.getPayload());
			System.out.println(node.get("protocolo").asText());
			String payload;
			switch (node.get("protocolo").asText()) {
			case "chat":
				payload = node.get("message").asText();
				SnakeGame sg = (SnakeGame) session.getAttributes().get(ROOM_ATT);
				Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
				StringBuilder sb = new StringBuilder();
				sb.append(String.format("{\"color\": \"%s\", \"message\": \"%s\"}", s.getHexColor(), payload));
				String msg = String.format("{\"type\": \"chat\",\"data\":[%s]}", sb.toString());
				sg.broadcast(msg);
				break;
			case "ping":
				return;
			case "create":
				payload = node.get("roomId").asText();
				createRoom(session, payload);
				break;
			case "join":
				payload = node.get("roomId").asText();
				joinRoom(session, payload);
				break;
			case "joinRandom":
				break;
			case "movement":
				payload = node.get("direction").asText();
				System.out.println(payload);
				controlMovement(session, payload);
				break;
			}

		} catch (Exception e) {
			System.err.println("Exception processing message " + message.getPayload());
			e.printStackTrace(System.err);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		System.out.println("Connection closed. Session " + session.getId());
		if ((boolean) session.getAttributes().get(PLAYING_ATT)) {
			Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
			SnakeGame sg = (SnakeGame) session.getAttributes().get(ROOM_ATT);
			sg.removeSnake(s);
			if(sg.getSnakes().isEmpty()) {
				lobby.remove(sg.getRoomId());
				sg.getSession().close();
			}
			String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
			sg.broadcast(msg);
		} else {
			snakesLobby.remove(session.getId());
		}

		// System.out.println("Connection closed. Session " + session.getId());
		//
		// Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
		//
		// snakeGame.removeSnake(s);
		//
		// String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
		//
		// snakeGame.broadcast(msg);
	}

}
