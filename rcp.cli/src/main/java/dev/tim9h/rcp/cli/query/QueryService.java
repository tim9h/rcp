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
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.cli.CliView;
import dev.tim9h.rcp.cli.query.bean.InstantAnswer;
import dev.tim9h.rcp.cli.query.bean.Suggestion;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;

@Singleton
public class QueryService {

	private static final String USER_AGENT = "User-Agent";

	private static final String USER_AGENT_VALUE = "Mozilla/5.0";

	private static final String METHOD_GET = "GET";

	private static final String URL_INSTANT_ANSWER = "https://api.duckduckgo.com/?q=%s&format=json";

	private static final int TIMEOUT_MILLIS = 3000;

	private static final Type TYPE_SUGGESTIONLIST = new TypeToken<ArrayList<Suggestion>>() {
	}.getType();

	@Inject
	private Gson gson;

	@Inject
	private Settings settings;

	@InjectLogger
	private Logger logger;

	public InstantAnswer query(String query) {
		HttpsURLConnection con = null;
		try {
			con = getConnection(URL_INSTANT_ANSWER, query);
			try (var in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
				return gson.fromJson(in, InstantAnswer.class);
			}
		} catch (IOException | JsonIOException | JsonSyntaxException | URISyntaxException e) {
			logger.warn(() -> "Unable to get instant answer", e);
			return null;
		} finally {
			disconnect(con);
		}
	}

	public List<String> getSuggestions(String query) {
		HttpsURLConnection con = null;
		try {
			con = getConnection(settings.getString(CliView.SETTING_SUGGEST_URL), query);
			try (var in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
				List<Suggestion> suggestions = gson.fromJson(in, TYPE_SUGGESTIONLIST);
				if (suggestions == null) {
					return Collections.emptyList();
				}
				return suggestions.stream().map(Suggestion::getPhrase).filter(Objects::nonNull).toList();
			}
		} catch (IOException | JsonIOException | JsonSyntaxException | URISyntaxException | IllegalFormatException e) {
			logger.warn(() -> "Unable to get suggestions", e);
			return Collections.emptyList();
		} finally {
			disconnect(con);
		}
	}

	private static void disconnect(HttpsURLConnection con) {
		if (con != null) {
			con.disconnect();
		}
	}

	private static HttpsURLConnection getConnection(String urlString, String query)
			throws IOException, URISyntaxException {
		var url = new URI(String.format(urlString, URLEncoder.encode(query, StandardCharsets.UTF_8))).toURL();
		var con = (HttpsURLConnection) url.openConnection();
		con.setRequestMethod(METHOD_GET);
		con.setRequestProperty(USER_AGENT, USER_AGENT_VALUE);
		con.setConnectTimeout(TIMEOUT_MILLIS);
		con.setReadTimeout(TIMEOUT_MILLIS);
		return con;
	}

}
