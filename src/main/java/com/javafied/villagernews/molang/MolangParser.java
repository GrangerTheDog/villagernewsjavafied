package com.javafied.villagernews.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recursive-descent parser for the subset of Bedrock Molang this add-on
 * actually uses: numbers, 'strings', v./t./c./q./Math./Array./Texture./
 * Geometry./Material. references, calls, indexing, unary ! and -, the usual
 * arithmetic/comparison/logical operators, {@code ??}, ternary and binary
 * {@code ?}, assignment, {@code { }} blocks and {@code return}. Loops,
 * {@code break}, {@code ->} and structs aren't used by it, so aren't here.
 *
 * <p>Precedence, loosest first: {@code =}, {@code ??}, {@code ?:},
 * {@code ||}, {@code &&}, {@code == !=}, {@code < <= > >=}, {@code + -},
 * {@code * /}, unary.
 */
final class MolangParser {
	private final String src;
	private int pos;
	private boolean sawTopLevelSemicolon;

	private MolangParser(String src) {
		this.src = src;
	}

	static MolangNode.Program parse(String source) {
		MolangParser p = new MolangParser(source);
		List<MolangNode> statements = p.statements(true);
		p.skipWhitespace();
		if (p.pos < p.src.length()) {
			throw p.error("unexpected '" + p.src.charAt(p.pos) + "'");
		}
		return new MolangNode.Program(statements, p.sawTopLevelSemicolon);
	}

	private List<MolangNode> statements(boolean topLevel) {
		List<MolangNode> out = new ArrayList<>();
		while (true) {
			skipWhitespace();
			if (pos >= src.length() || peek('}')) {
				return out;
			}
			if (peek(';')) {
				pos++;
				if (topLevel) {
					sawTopLevelSemicolon = true;
				}
				continue;
			}
			out.add(statement());
			skipWhitespace();
			if (peek(';')) {
				pos++;
				if (topLevel) {
					sawTopLevelSemicolon = true;
				}
			} else if (pos < src.length() && !peek('}')) {
				throw error("expected ';'");
			}
		}
	}

	private MolangNode statement() {
		if (keyword("return")) {
			return new MolangNode.Return(expression());
		}
		return expression();
	}

	private MolangNode expression() {
		MolangNode left = coalesce();
		skipWhitespace();
		if (peek('=') && !peekAt(1, '=')) {
			pos++;
			if (!(left instanceof MolangNode.Var target)) {
				throw error("can only assign to v./t. variables");
			}
			return new MolangNode.Assign(target, expression());
		}
		return left;
	}

	private MolangNode coalesce() {
		MolangNode left = conditional();
		while (match("??")) {
			left = new MolangNode.Binary("??", left, conditional());
		}
		return left;
	}

	private MolangNode conditional() {
		MolangNode condition = binary(0);
		skipWhitespace();
		if (peek('?') && !peekAt(1, '?')) {
			pos++;
			MolangNode then = expression();
			MolangNode otherwise = match(":") ? expression() : null;
			return new MolangNode.Conditional(condition, then, otherwise);
		}
		return condition;
	}

	private static final String[][] BINARY_LEVELS = {
			{"||"}, {"&&"}, {"==", "!="}, {"<=", ">=", "<", ">"}, {"+", "-"}, {"*", "/"}
	};

	private MolangNode binary(int level) {
		if (level == BINARY_LEVELS.length) {
			return unary();
		}
		MolangNode left = binary(level + 1);
		outer:
		while (true) {
			for (String op : BINARY_LEVELS[level]) {
				if (match(op)) {
					left = new MolangNode.Binary(op, left, binary(level + 1));
					continue outer;
				}
			}
			return left;
		}
	}

	private MolangNode unary() {
		skipWhitespace();
		if (peek('!') && !peekAt(1, '=')) {
			pos++;
			return new MolangNode.Unary('!', unary());
		}
		if (peek('-')) {
			pos++;
			return new MolangNode.Unary('-', unary());
		}
		return primary();
	}

	private MolangNode primary() {
		skipWhitespace();
		if (pos >= src.length()) {
			throw error("unexpected end");
		}
		char c = src.charAt(pos);
		if (c == '(') {
			pos++;
			MolangNode inner = expression();
			expect(')');
			return inner;
		}
		if (c == '{') {
			pos++;
			List<MolangNode> body = statements(false);
			expect('}');
			return new MolangNode.Block(body);
		}
		if (c == '\'') {
			int end = src.indexOf('\'', pos + 1);
			if (end < 0) {
				throw error("unterminated string");
			}
			String value = src.substring(pos + 1, end);
			pos = end + 1;
			return new MolangNode.Str(value);
		}
		if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
			return number();
		}
		if (Character.isLetter(c) || c == '_') {
			return identifier();
		}
		throw error("unexpected '" + c + "'");
	}

	private MolangNode number() {
		int start = pos;
		while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
			pos++;
		}
		double value = Double.parseDouble(src.substring(start, pos));
		if (pos < src.length() && (src.charAt(pos) == 'f' || src.charAt(pos) == 'F')) {
			pos++;
		}
		return new MolangNode.Num(value);
	}

	private MolangNode identifier() {
		String path = readPath();
		int dot = path.indexOf('.');
		if (dot < 0) {
			return switch (path.toLowerCase(Locale.ROOT)) {
				case "true" -> new MolangNode.Num(1);
				case "false" -> new MolangNode.Num(0);
				case "loop", "for_each", "break", "continue" -> throw error("unsupported: " + path);
				default -> new MolangNode.Num(0);
			};
		}
		String ns = path.substring(0, dot).toLowerCase(Locale.ROOT);
		String name = path.substring(dot + 1).toLowerCase(Locale.ROOT);
		return switch (ns) {
			case "v", "variable" -> new MolangNode.Var('v', name);
			case "t", "temp" -> new MolangNode.Var('t', name);
			case "c", "context" -> new MolangNode.Var('c', name);
			case "q", "query" -> new MolangNode.Query(name, optionalArgs());
			case "math" -> new MolangNode.MathCall(name, optionalArgs());
			case "texture", "geometry", "material" -> new MolangNode.Resource(ns, name);
			case "array" -> {
				expect('[');
				MolangNode index = expression();
				expect(']');
				yield new MolangNode.ArrayIndex(name, index);
			}
			default -> throw error("unknown namespace '" + ns + "'");
		};
	}

	private String readPath() {
		int start = pos;
		while (true) {
			while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
				pos++;
			}
			if (pos + 1 < src.length() && src.charAt(pos) == '.'
					&& (Character.isLetter(src.charAt(pos + 1)) || src.charAt(pos + 1) == '_')) {
				pos++;
				continue;
			}
			return src.substring(start, pos);
		}
	}

	private List<MolangNode> optionalArgs() {
		skipWhitespace();
		if (!peek('(')) {
			return List.of();
		}
		pos++;
		List<MolangNode> args = new ArrayList<>();
		skipWhitespace();
		if (peek(')')) {
			pos++;
			return args;
		}
		do {
			args.add(expression());
		} while (match(","));
		expect(')');
		return args;
	}

	private boolean keyword(String word) {
		skipWhitespace();
		if (src.regionMatches(true, pos, word, 0, word.length())) {
			int after = pos + word.length();
			if (after >= src.length() || !(Character.isLetterOrDigit(src.charAt(after)) || src.charAt(after) == '_')) {
				pos = after;
				return true;
			}
		}
		return false;
	}

	private boolean match(String token) {
		skipWhitespace();
		if (src.startsWith(token, pos)) {
			// Don't let "<" swallow the start of "<=", or "?" the start of "??".
			if (token.length() == 1 && pos + 1 < src.length()) {
				char next = src.charAt(pos + 1);
				if ((token.equals("<") || token.equals(">")) && next == '=') {
					return false;
				}
			}
			pos += token.length();
			return true;
		}
		return false;
	}

	private void expect(char c) {
		skipWhitespace();
		if (!peek(c)) {
			throw error("expected '" + c + "'");
		}
		pos++;
	}

	private boolean peek(char c) {
		return pos < src.length() && src.charAt(pos) == c;
	}

	private boolean peekAt(int offset, char c) {
		return pos + offset < src.length() && src.charAt(pos + offset) == c;
	}

	private void skipWhitespace() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}

	private MolangException error(String message) {
		return new MolangException(message + " at " + pos + " in: " + src);
	}
}
