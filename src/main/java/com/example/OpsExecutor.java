package com.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

/**
 * Исполнитель сгенерированной ОПС.
 * Поддерживает арифметику, сравнения, логические операции, метки и переходы (j/jf),
 * и специальные операции n/ar/f/i/s/o.
 */
public final class OpsExecutor {

    private record ArrayRef(String name, int index) {}

    public record ExecResult(List<String> output, Map<String, Object> variables) {}

    private final Deque<Object> stack = new ArrayDeque<>(); // вычислительный стек
    private final Map<String, Object> variables = new HashMap<>(); // таблица переменных
    private final Deque<String> initStack = new ArrayDeque<>(); // последняя инициализированная переменная
    private final List<String> output = new ArrayList<>();
    private final Deque<Long> inputBuffer = new ArrayDeque<>();
    private final Scanner scanner = new Scanner(System.in);

    public ExecResult execute(List<Parser.OpsElement> ops) {
        Objects.requireNonNull(ops, "ops");
        int ip = 0;
        while (ip < ops.size()) {
            Parser.OpsElement el = ops.get(ip);
            String val = el.value();
            String type = el.type();

            if ("identifier".equals(type)) {
                stack.push(val); // имя, значение подтянем при операции
                ip++;
                continue;
            }
            if ("number".equals(type)) {
                stack.push(parseNumber(val));
                ip++;
                continue;
            }
            if ("label".equals(type) || "label-placeholder".equals(type)) {
                stack.push(val);
                ip++;
                continue;
            }

            switch (val) {
                case "+" -> {
                    long b = asLong(popValue());
                    long a = asLong(popValue());
                    stack.push(a + b);
                }
                case "-" -> {
                    long b = asLong(popValue());
                    long a = asLong(popValue());
                    stack.push(a - b);
                }
                case "*" -> {
                    long b = asLong(popValue());
                    long a = asLong(popValue());
                    stack.push(a * b);
                }
                case "/" -> {
                    long b = asLong(popValue());
                    long a = asLong(popValue());
                    stack.push(a / b);
                }
                case "-'" -> {
                    long a = asLong(popValue());
                    stack.push(-a);
                }
                case "<", ">", "<=", ">=", "==", "!=" -> {
                    long b = asLong(popValue());
                    long a = asLong(popValue());
                    boolean res = switch (val) {
                        case "<" -> a < b;
                        case ">" -> a > b;
                        case "<=" -> a <= b;
                        case ">=" -> a >= b;
                        case "==" -> a == b;
                        case "!=" -> a != b;
                        default -> false;
                    };
                    stack.push(res);
                }
                case "AND" -> {
                    boolean b = asBoolean(popValue());
                    boolean a = asBoolean(popValue());
                    stack.push(a && b);
                }
                case "OR" -> {
                    boolean b = asBoolean(popValue());
                    boolean a = asBoolean(popValue());
                    stack.push(a || b);
                }
                case "!" -> {
                    boolean a = asBoolean(popValue());
                    stack.push(!a);
                }
                case "jf" -> {
                    Object targetObj = popValue();
                    boolean cond = asBoolean(popValue());
                    int target = parseLabel(targetObj);
                    if (!cond) {
                        ip = target;
                        continue;
                    }
                }
                case "j" -> {
                    Object targetObj = popValue();
                    int target = parseLabel(targetObj);
                    ip = target;
                    continue;
                }
                case "n" -> {
                    String name = asIdentifier(popValue());
                    variables.putIfAbsent(name, 0L);
                    initStack.push(name);
                }
                case "ar" -> {
                    long size = asLong(popValue());
                    String name = asIdentifier(popValue());
                    int len = (int) size;
                    long[] arr = new long[len];
                    variables.put(name, arr);
                    initStack.push(name);
                }
                case "f" -> {
                    if (initStack.isEmpty()) {
                        throw new IllegalStateException("Пустой стек инициализаций при операции f");
                    }
                    String name = initStack.peek();
                    Object value = popValue();
                    variables.put(name, value);
                }
                case "i" -> {
                    long idxNum = asLong(popValue());
                    String name = asIdentifier(popValue());
                    int idx = (int) idxNum;
                    stack.push(new ArrayRef(name, idx));
                }
                case "s" -> {
                    String name = asIdentifier(popValue());
                    long read = nextInputValue(name);
                    variables.put(name, read);
                    stack.push(read);
                }
                case "o" -> {
                    Object raw = popValue();
                    output.add(formatOutputValue(raw));
                }
                case ":=", "=" -> {
                    Object value = resolveValue(popValue()); // правую часть обязательно разворачиваем
                    Object target = popValue();
                    if (target instanceof ArrayRef ref) {
                        assignArray(ref, value);
                    } else {
                        String name = asIdentifier(target);
                        variables.put(name, value);
                    }
                }
                case ":" -> {
                    // игнорируем, используется только для синтаксического построения
                }
                default -> throw new IllegalStateException("Неизвестная операция: " + val);
            }
            ip++;
        }

        return new ExecResult(List.copyOf(output), Map.copyOf(variables));
    }

    private Object popValue() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Стек значений пуст");
        }
        return stack.pop();
    }

    private long asLong(Object obj) {
        if (obj instanceof Number num) {
            return num.longValue();
        }
        if (obj instanceof ArrayRef ref) {
            return readArray(ref);
        }
        if (obj instanceof String s) {
            Object val = variables.get(s);
            if (val == null) {
                throw new IllegalStateException("Переменная '" + s + "' не инициализирована");
            }
            if (val instanceof Number numVal) {
                return numVal.longValue();
            }
            throw new IllegalStateException("Ожидалось целое число, встретился объект '" + s + "' типа " + val.getClass().getSimpleName());
        }
        throw new IllegalStateException("Ожидалось целое число, встретилось " + obj);
    }

    private boolean asBoolean(Object obj) {
        if (obj instanceof Boolean b) {
            return b;
        }
        if (obj instanceof Number n) {
            return n.longValue() != 0L;
        }
        if (obj instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        throw new IllegalStateException("Ожидалось логическое значение, встретилось " + obj);
    }

    private String asIdentifier(Object obj) {
        if (obj instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Ожидался идентификатор, встретилось " + obj);
    }

    private Object resolveValue(Object obj) {
        if (obj instanceof ArrayRef ref) {
            return readArray(ref);
        }
        if (obj instanceof String s) {
            if (variables.containsKey(s)) {
                return variables.get(s);
            }
            return s;
        }
        return obj;
    }

    private String formatOutputValue(Object obj) {
        if (obj instanceof String s && variables.containsKey(s)) {
            Object val = variables.get(s);
            return s + "=" + val;
        }
        Object resolved = resolveValue(obj);
        return String.valueOf(resolved);
    }

    private int parseLabel(Object obj) {
        if (!(obj instanceof String s) || !s.startsWith("M")) {
            throw new IllegalStateException("Ожидалась метка вида M<number>, встретилось " + obj);
        }
        return Integer.parseInt(s.substring(1));
    }

    private long nextInputValue(String name) {
        if (!inputBuffer.isEmpty()) {
            return inputBuffer.pollFirst();
        }
        System.out.print("INPUT " + name + ": ");
        System.out.flush();
        while (true) {
            if (scanner.hasNextLong()) {
                return scanner.nextLong();
            }
            // пропускаем некорректный токен
            scanner.next();
            System.out.print("Введите целое значение для " + name + ": ");
            System.out.flush();
        }
    }

    private long readArray(ArrayRef ref) {
        Object arrObj = variables.get(ref.name());
        if (!(arrObj instanceof long[] arr)) {
            throw new IllegalStateException("Ожидался массив для " + ref.name());
        }
        int idx = ref.index();
        if (idx < 0 || idx >= arr.length) {
            throw new IndexOutOfBoundsException("Индекс " + idx + " вне массива " + ref.name());
        }
        return arr[idx];
    }

    private void assignArray(ArrayRef ref, Object value) {
        Object arrObj = variables.get(ref.name());
        if (!(arrObj instanceof long[] arr)) {
            throw new IllegalStateException("Ожидался массив для " + ref.name());
        }
        int idx = ref.index();
        if (idx < 0 || idx >= arr.length) {
            throw new IndexOutOfBoundsException("Индекс " + idx + " вне массива " + ref.name());
        }
        long val = asLong(value);
        arr[idx] = val;
    }

    private Number parseNumber(String val) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Не удалось распарсить целое число: " + val, e);
        }
    }
}
