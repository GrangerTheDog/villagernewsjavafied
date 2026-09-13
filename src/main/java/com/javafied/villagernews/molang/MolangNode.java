package com.javafied.villagernews.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Compiled Molang: a small AST evaluated directly against a {@link MolangEvaluation}. */
public sealed interface MolangNode {
	Object eval(MolangEvaluation e);

	record Num(double value) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			return value;
		}
	}

	record Str(String value) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			return value;
		}
	}

	/** {@code v.x}, {@code t.x} or {@code c.x}; {@code ns} is the canonical one-letter namespace. */
	record Var(char ns, String name) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			return switch (ns) {
				case 'v' -> e.env.variable(name);
				case 't' -> e.temp(name);
				default -> e.env.context(name);
			};
		}

		void assign(MolangEvaluation e, Object value) {
			if (ns == 'v') {
				e.env.setVariable(name, value);
			} else if (ns == 't') {
				e.temps.put(name, value);
			}
		}
	}

	record Query(String name, List<MolangNode> args) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			List<Object> values = new ArrayList<>(args.size());
			for (MolangNode arg : args) {
				values.add(arg.eval(e));
			}
			return switch (name) {
				// Pure helpers, no entity needed.
				case "any" -> {
					for (int i = 1; i < values.size(); i++) {
						if (Molang.equal(values.get(0), values.get(i))) {
							yield 1.0;
						}
					}
					yield 0.0;
				}
				case "in_range" -> values.size() == 3
						&& Molang.num(values.get(0)) >= Molang.num(values.get(1))
						&& Molang.num(values.get(0)) <= Molang.num(values.get(2)) ? 1.0 : 0.0;
				default -> e.env.query(name, values);
			};
		}
	}

	record MathCall(String name, List<MolangNode> args) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			double[] a = new double[args.size()];
			for (int i = 0; i < a.length; i++) {
				a[i] = Molang.num(args.get(i).eval(e));
			}
			return switch (name) {
				case "abs" -> Math.abs(a[0]);
				case "ceil" -> Math.ceil(a[0]);
				case "floor" -> Math.floor(a[0]);
				case "round" -> (double) Math.round(a[0]);
				case "trunc" -> a[0] < 0 ? Math.ceil(a[0]) : Math.floor(a[0]);
				case "sqrt" -> Math.sqrt(a[0]);
				case "exp" -> Math.exp(a[0]);
				case "ln" -> Math.log(a[0]);
				case "pow" -> Math.pow(a[0], a[1]);
				case "min" -> Math.min(a[0], a[1]);
				case "max" -> Math.max(a[0], a[1]);
				case "mod" -> a[0] % a[1];
				case "clamp" -> Math.max(a[1], Math.min(a[2], a[0]));
				case "lerp" -> a[0] + (a[1] - a[0]) * a[2];
				// Molang trig works in degrees.
				case "sin" -> Math.sin(Math.toRadians(a[0]));
				case "cos" -> Math.cos(Math.toRadians(a[0]));
				case "asin" -> Math.toDegrees(Math.asin(a[0]));
				case "acos" -> Math.toDegrees(Math.acos(a[0]));
				case "atan" -> Math.toDegrees(Math.atan(a[0]));
				case "atan2" -> Math.toDegrees(Math.atan2(a[0], a[1]));
				case "pi" -> Math.PI;
				case "random" -> a[0] + ThreadLocalRandom.current().nextDouble() * (a[1] - a[0]);
				case "random_integer" -> (double) Math.round(a[0] + ThreadLocalRandom.current().nextDouble() * (a[1] - a[0]));
				case "die_roll" -> {
					double sum = 0;
					for (int i = 0; i < (int) a[0]; i++) {
						sum += a[1] + ThreadLocalRandom.current().nextDouble() * (a[2] - a[1]);
					}
					yield sum;
				}
				default -> 0.0;
			};
		}
	}

	/** {@code Texture.x}, {@code Geometry.x}, {@code Material.x}. */
	record Resource(String kind, String name) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			return e.env.resource(kind, name);
		}
	}

	record ArrayIndex(String name, MolangNode index) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			String element = e.env.arrayElement(name, (int) Math.floor(Molang.num(index.eval(e))));
			return element == null ? null : Molang.compile(element).eval(new MolangEvaluation(e.env));
		}
	}

	record Unary(char op, MolangNode operand) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			Object v = operand.eval(e);
			return op == '!' ? (Molang.truthy(v) ? 0.0 : 1.0) : -Molang.num(v);
		}
	}

	record Binary(String op, MolangNode left, MolangNode right) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			switch (op) {
				case "&&":
					return Molang.truthy(left.eval(e)) && Molang.truthy(right.eval(e)) ? 1.0 : 0.0;
				case "||":
					return Molang.truthy(left.eval(e)) || Molang.truthy(right.eval(e)) ? 1.0 : 0.0;
				case "??": {
					Object l = left.eval(e);
					return l != null ? l : right.eval(e);
				}
				default:
					break;
			}
			Object l = left.eval(e);
			Object r = right.eval(e);
			return switch (op) {
				case "==" -> Molang.equal(l, r) ? 1.0 : 0.0;
				case "!=" -> Molang.equal(l, r) ? 0.0 : 1.0;
				case "<" -> Molang.num(l) < Molang.num(r) ? 1.0 : 0.0;
				case "<=" -> Molang.num(l) <= Molang.num(r) ? 1.0 : 0.0;
				case ">" -> Molang.num(l) > Molang.num(r) ? 1.0 : 0.0;
				case ">=" -> Molang.num(l) >= Molang.num(r) ? 1.0 : 0.0;
				case "+" -> Molang.num(l) + Molang.num(r);
				case "-" -> Molang.num(l) - Molang.num(r);
				case "*" -> Molang.num(l) * Molang.num(r);
				case "/" -> {
					double d = Molang.num(r);
					yield d == 0 ? 0.0 : Molang.num(l) / d;
				}
				default -> 0.0;
			};
		}
	}

	/** {@code a ? b : c}, or the statement form {@code a ? b} when {@code otherwise} is null. */
	record Conditional(MolangNode condition, MolangNode then, MolangNode otherwise) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			if (Molang.truthy(condition.eval(e))) {
				return then.eval(e);
			}
			return otherwise == null ? 0.0 : otherwise.eval(e);
		}
	}

	record Assign(Var target, MolangNode value) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			Object v = value.eval(e);
			target.assign(e, v);
			return v;
		}
	}

	record Return(MolangNode value) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			Object v = value.eval(e);
			if (!e.returned) {
				e.returned = true;
				e.returnValue = v;
			}
			return v;
		}
	}

	record Block(List<MolangNode> statements) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			for (MolangNode statement : statements) {
				statement.eval(e);
				if (e.returned) {
					break;
				}
			}
			return 0.0;
		}
	}

	/**
	 * A whole expression. Bedrock distinguishes a "simple" expression (no
	 * {@code ;}), whose value is the expression itself, from a "complex" one,
	 * whose value is whatever it {@code return}s, or 0.
	 */
	record Program(List<MolangNode> statements, boolean complex) implements MolangNode {
		public Object eval(MolangEvaluation e) {
			Object last = 0.0;
			for (MolangNode statement : statements) {
				last = statement.eval(e);
				if (e.returned) {
					return e.returnValue;
				}
			}
			return complex ? 0.0 : Objects.requireNonNullElse(last, 0.0);
		}
	}
}
