package com.javafied.villagernews.molang;

import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.ExpressionInterpreter;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.ObjectProperty;
import team.unnamed.mocha.runtime.value.ObjectValue;
import team.unnamed.mocha.runtime.value.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * A parsed Molang program, run on unnamed/mocha's interpreter.
 *
 * <p>Only mocha's parser and interpreter are used, never {@code MochaEngine}:
 * the engine eagerly builds a javassist bytecode compiler we don't need (and
 * don't ship). Sources are parsed once and cached; one that fails to parse is
 * reported once and then evaluates to 0, like Bedrock does.
 */
public final class MolangProgram {
	private static final Map<String, MolangProgram> CACHE = new ConcurrentHashMap<>();
	private static final MolangProgram ZERO = new MolangProgram(List.of());
	private static final ObjectValue MATH = new BedrockMath();
	private static volatile Consumer<String> errorReporter = message -> {
	};

	private final List<Expression> expressions;

	private MolangProgram(List<Expression> expressions) {
		this.expressions = expressions;
	}

	public static void setErrorReporter(Consumer<String> reporter) {
		errorReporter = reporter;
	}

	public static MolangProgram of(String source) {
		return CACHE.computeIfAbsent(source, s -> {
			try {
				return new MolangProgram(MolangParser.parseAll(s));
			} catch (Exception e) {
				errorReporter.accept("Unparseable Molang (" + e.getMessage() + "): " + s);
				return ZERO;
			}
		});
	}

	/** A fresh scope with the standard {@code math} namespace; callers add v./q./t./texture. etc. */
	public static Scope newScope() {
		Scope scope = Scope.create();
		scope.set("math", MATH);
		return scope;
	}

	/**
	 * Bedrock semantics: the value of a {@code return} if one runs (even from
	 * inside a {@code { }} block), else the value of a lone expression, else 0.
	 */
	public Value eval(Scope scope) {
		ExpressionInterpreter<Object> interpreter = new ExpressionInterpreter<>(null, scope);
		Value last = Value.nil();
		for (Expression expression : expressions) {
			last = expression.visit(interpreter);
			Value returned = interpreter.popReturnValue();
			if (returned != null) {
				return returned;
			}
		}
		return expressions.size() == 1 ? last : Value.nil();
	}

	public double evalNumber(Scope scope) {
		return eval(scope).getAsNumber();
	}

	public boolean evalBoolean(Scope scope) {
		return eval(scope).getAsBoolean();
	}

	/**
	 * mocha's standard math, except {@code random_integer}: mocha uses
	 * {@code nextInt(min, max)}, which excludes {@code max} (and throws when
	 * they're equal), while Bedrock's is inclusive on both ends - so e.g. the
	 * add-on's {@code Math.random_integer(0,1)} could never return 1.
	 */
	private static final class BedrockMath implements ObjectValue {
		private final ObjectValue standard = JavaObjectBinding.of(MochaMath.class, null, new MochaMath());
		private final ObjectProperty randomInteger = ObjectProperty.property((Function<Object>) (ctx, args) -> {
			int a = (int) args.next().eval().getAsNumber();
			int b = (int) args.next().eval().getAsNumber();
			return Value.of(ThreadLocalRandom.current().nextInt(Math.min(a, b), Math.max(a, b) + 1));
		}, true);

		@Override
		public ObjectProperty getProperty(String name) {
			return name.equalsIgnoreCase("random_integer") ? randomInteger : standard.getProperty(name);
		}
	}
}
