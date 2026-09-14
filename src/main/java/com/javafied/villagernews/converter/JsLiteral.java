package com.javafied.villagernews.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JavaScript object/array literals out of a (minified) script as JSON,
 * without running any of it: strings, numbers ({@code .5}, {@code 8e3}),
 * {@code true/false/!0/!1/null/void 0}, unquoted and numeric keys, computed
 * keys ({@code [name]:}), spreads ({@code ...name}) and the little arithmetic
 * the add-on puts in them ({@code 2*(a.b.c??0)}). Identifiers are
 * resolved through {@link #setResolver} - by default, to the literal the script
 * assigns them at top level ({@code name={...}}).
 */
final class JsLiteral {
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

	private final String source;
	private final Map<String, JsonElement> constants = new HashMap<>();
	/** Resolves an identifier used as a value; may return null for "unknown". */
	private Function<String, JsonElement> resolver = this::topLevelConstant;
	private int pos;

	JsLiteral(String source) {
		this.source = source;
	}

	/** Checked before the default top-level-constant lookup; return null to fall through. */
	void setResolver(Function<String, JsonElement> override) {
		this.resolver = name -> {
			JsonElement value = override.apply(name);
			return value != null ? value : topLevelConstant(name);
		};
	}

	/** Parses the literal starting at {@code start} (which must be {@code {} or {@code [}...). */
	JsonElement parseAt(int start) {
		int saved = pos;
		pos = start;
		try {
			return value();
		} finally {
			pos = saved;
		}
	}

	private JsonElement topLevelConstant(String name) {
		if (constants.containsKey(name)) {
			return constants.get(name);
		}
		constants.put(name, null); // guards against self-reference
		Matcher m = Pattern.compile("(?<![\\w$.])" + Pattern.quote(name) + "=(?=[{\\[\"'\\d.!-])").matcher(source);
		JsonElement value = null;
		if (m.find()) {
			try {
				value = parseAt(m.end());
			} catch (IllegalStateException e) {
				value = null;
			}
		}
		constants.put(name, value);
		return value;
	}

	/** {@code a ?? b}, then {@code + -}, then {@code * /}; enough for computed cooldowns. */
	private JsonElement value() {
		JsonElement left = additive();
		while (true) {
			skipSpace();
			if (!source.startsWith("??", pos)) {
				return left;
			}
			pos += 2;
			JsonElement right = additive();
			if (left == null || left.isJsonNull()) {
				left = right;
			}
		}
	}

	private JsonElement additive() {
		JsonElement left = multiplicative();
		while (true) {
			skipSpace();
			char c = peek();
			if (c != '+' && c != '-') {
				return left;
			}
			pos++;
			double right = multiplicative().getAsDouble();
			left = new JsonPrimitive(c == '+' ? left.getAsDouble() + right : left.getAsDouble() - right);
		}
	}

	private JsonElement multiplicative() {
		JsonElement left = member();
		while (true) {
			skipSpace();
			char c = peek();
			if (c != '*' && c != '/') {
				return left;
			}
			pos++;
			double right = member().getAsDouble();
			left = new JsonPrimitive(c == '*' ? left.getAsDouble() * right : left.getAsDouble() / right);
		}
	}

	/** A primary followed by any {@code .name} accesses; a missing member reads as null, like {@code undefined}. */
	private JsonElement member() {
		JsonElement value = primary();
		while (pos < source.length() && peek() == '.' && !Character.isDigit(source.charAt(pos + 1))) {
			pos++;
			String name = identifier();
			value = value != null && value.isJsonObject() ? value.getAsJsonObject().get(name) : null;
			if (value == null) {
				value = JsonNull.INSTANCE;
			}
		}
		return value;
	}

	private JsonElement primary() {
		skipSpace();
		char c = peek();
		switch (c) {
			case '{':
				return object();
			case '[':
				return array();
			case '"':
			case '\'':
			case '`':
				return new JsonPrimitive(string());
			case '(':
				pos++;
				JsonElement inner = value();
				skipSpace();
				expect(')');
				return inner;
			case '!':
				pos++;
				return new JsonPrimitive(!truthy(primary()));
			case '-':
				pos++;
				return new JsonPrimitive(-primary().getAsDouble());
			default:
				if (Character.isDigit(c) || c == '.') {
					return new JsonPrimitive(number());
				}
				String word = identifier();
				switch (word) {
					case "true":
						return new JsonPrimitive(true);
					case "false":
						return new JsonPrimitive(false);
					case "null":
						return JsonNull.INSTANCE;
					case "void":
						primary();
						return JsonNull.INSTANCE;
					default:
						JsonElement resolved = resolver.apply(word);
						if (resolved == null) {
							throw new IllegalStateException("unresolvable identifier " + word + " at " + pos);
						}
						return resolved;
				}
		}
	}

	private JsonObject object() {
		expect('{');
		JsonObject object = new JsonObject();
		while (true) {
			skipSpace();
			if (peek() == '}') {
				pos++;
				return object;
			}
			if (source.startsWith("...", pos)) {
				pos += 3;
				JsonElement spread = value();
				if (spread.isJsonObject()) {
					spread.getAsJsonObject().entrySet().forEach(e -> object.add(e.getKey(), e.getValue()));
				}
			} else {
				String key = key();
				skipSpace();
				expect(':');
				object.add(key, value());
			}
			skipSpace();
			if (peek() == ',') {
				pos++;
			}
		}
	}

	private String key() {
		skipSpace();
		char c = peek();
		if (c == '"' || c == '\'') {
			return string();
		}
		if (c == '[') {
			pos++;
			JsonElement computed = value();
			skipSpace();
			expect(']');
			return computed.getAsString();
		}
		if (Character.isDigit(c) || c == '.') {
			return formatNumberKey(number());
		}
		return identifier();
	}

	/** JS turns numeric keys into strings the way it prints numbers: {@code .87} -> "0.87", {@code 0} -> "0". */
	private static String formatNumberKey(double n) {
		return n == Math.rint(n) && Math.abs(n) < 1e15 ? Long.toString((long) n) : Double.toString(n);
	}

	private JsonArray array() {
		expect('[');
		JsonArray array = new JsonArray();
		while (true) {
			skipSpace();
			if (peek() == ']') {
				pos++;
				return array;
			}
			if (source.startsWith("...", pos)) {
				pos += 3;
				JsonElement spread = value();
				if (spread.isJsonArray()) {
					array.addAll(spread.getAsJsonArray());
				}
			} else {
				array.add(value());
			}
			skipSpace();
			if (peek() == ',') {
				pos++;
			}
		}
	}

	private String string() {
		char quote = source.charAt(pos++);
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = source.charAt(pos++);
			if (c == quote) {
				return sb.toString();
			}
			if (quote == '`' && c == '$' && peek() == '{') {
				throw new IllegalStateException("template literal with substitutions at " + pos);
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char escaped = source.charAt(pos++);
			switch (escaped) {
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'r' -> sb.append('\r');
				case 'u' -> {
					sb.append((char) Integer.parseInt(source.substring(pos, pos + 4), 16));
					pos += 4;
				}
				default -> sb.append(escaped);
			}
		}
	}

	private double number() {
		int start = pos;
		while (pos < source.length()) {
			char c = source.charAt(pos);
			boolean exponentSign = (c == '+' || c == '-') && pos > start
					&& Character.toLowerCase(source.charAt(pos - 1)) == 'e';
			if (!(Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || exponentSign)) {
				break;
			}
			pos++;
		}
		return Double.parseDouble(source.substring(start, pos));
	}

	private String identifier() {
		Matcher m = IDENTIFIER.matcher(source).region(pos, source.length());
		if (!m.lookingAt()) {
			throw new IllegalStateException("unexpected '" + peek() + "' at " + pos);
		}
		pos = m.end();
		return m.group();
	}

	private static boolean truthy(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return false;
		}
		if (!e.isJsonPrimitive()) {
			return true;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		if (p.isNumber()) {
			return p.getAsDouble() != 0;
		}
		return !p.getAsString().isEmpty();
	}

	private void skipSpace() {
		while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
			pos++;
		}
	}

	private char peek() {
		if (pos >= source.length()) {
			throw new IllegalStateException("unexpected end of script");
		}
		return source.charAt(pos);
	}

	private void expect(char c) {
		if (peek() != c) {
			throw new IllegalStateException("expected '" + c + "' but found '" + peek() + "' at " + pos);
		}
		pos++;
	}
}
