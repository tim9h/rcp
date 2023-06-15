package dev.tim9h.rcp.cli.query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.cli.query.bean.InstantAnswer;
import dev.tim9h.rcp.cli.query.bean.Suggestion;
import dev.tim9h.rcp.logging.InjectLogger;

@Singleton
public class QueryService {

	private static final String USER_AGENT = "User-Agent";

	private static final String USER_AGENT_VALUE = "Mozilla/5.0";

	private static final String METHOD_GET = "GET";

	private static final String URL_INSTANT_ANSWER = "https://api.duckduckgo.com/?q=%s&format=json";

	private static final String URL_SUGGESTIONS = "https://ac.duckduckgo.com/ac/?q=%s&type=json";

	private static final Type TYPE_SUGGESTIONLIST = new TypeToken<ArrayList<Suggestion>>() {
	}.getType();

	@Inject
	private Gson gson;

	@InjectLogger
	private Logger logger;

	public InstantAnswer query(String query) {
		try (var in = new BufferedReader(
				new InputStreamReader(getConnection(URL_INSTANT_ANSWER, query).getInputStream()))) {
			return gson.fromJson(in, InstantAnswer.class);

		} catch (IOException | JsonIOException | JsonSyntaxException | URISyntaxException e) {
			logger.error(() -> "Unable to get instant answer", e);
			return null;
		}
	}

	public List<String> getSuggestions(String query) {
		try (var in = new BufferedReader(
				new InputStreamReader(getConnection(URL_SUGGESTIONS, query).getInputStream()))) {
			List<Suggestion> suggestions = gson.fromJson(in, TYPE_SUGGESTIONLIST);
			return suggestions.stream().map(Suggestion::getPhrase).toList();

		} catch (IOException | URISyntaxException e) {
			logger.error(() -> "Unable to get suggestions", e);
			return Collections.emptyList();
		}
	}

	private static HttpsURLConnection getConnection(String urlString, String query)
			throws IOException, URISyntaxException {
		var url = new URI(String.format(urlString, URLEncoder.encode(query, StandardCharsets.UTF_8))).toURL();
		var con = (HttpsURLConnection) url.openConnection();
		con.setRequestMethod(METHOD_GET);
		con.setRequestProperty(USER_AGENT, USER_AGENT_VALUE);
		return con;
	}

}
