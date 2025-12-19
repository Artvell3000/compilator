package com.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LL(1) синтаксический анализатор, который по входной программе строит строку ОПС
 * (берет значения из столбца ОПС правил {@link GreibachGrammar}).
 */
public class Parser {

    private static final String EPSILON = "λ";
    private static final String END = "$";

    private final GreibachGrammar grammar;
    private final Lexer lexer;
    private final Set<String> nonTerminals;
    private final Set<String> terminals;

    private final Map<String, Set<String>> firstCache = new HashMap<>();
    private final Map<String, Set<String>> followCache = new HashMap<>();

    private List<String> inputSymbols;
    private List<Lexer.Token> inputTokens;
    private int position;
    private final List<OpsElement> opsOutput = new ArrayList<>(); // итоговая ОПС
    private String lastMatchedLexeme = "";
    private String lastMatchedKind = "";
    private final Deque<Integer> exitLabelStack = new ArrayDeque<>(); // адрес выхода из if/while
    private final Deque<Integer> loopStartLabelStack = new ArrayDeque<>(); // адрес начала цикла
    private int recursionDepth = 0;
    private boolean loggingEnabled = true;
    private boolean pendingAssignOp = false;

    public record OpsElement(String value, String type) {}

    public record Result(String opsString, List<OpsElement> opsSequence) {}

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    public Parser(GreibachGrammar grammar, Lexer lexer) {
        this.grammar = Objects.requireNonNull(grammar, "grammar");
        this.lexer = Objects.requireNonNull(lexer, "lexer");
        this.nonTerminals = grammar.allRules().keySet();
        this.terminals = collectTerminals();
        initializeFollow();
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public Result parse(String source) {
        Objects.requireNonNull(source, "source");
        List<Lexer.Token> tokens = lexer.tokenize(source);
        // Преобразуем поток токенов в последовательность терминалов грамматики
        this.inputSymbols = new ArrayList<>(tokens.size() + 1);
        this.inputTokens = new ArrayList<>(tokens);
        for (Lexer.Token token : tokens) {
            String term = mapTokenToTerminal(token);
            if (!terminals.contains(term)) {
                throw new ParseException("Токен '" + token.lexeme() + "' не покрыт терминалами грамматики");
            }
            inputSymbols.add(term);
        }
        inputSymbols.add(END);
        this.position = 0;
        this.opsOutput.clear();
        this.lastMatchedLexeme = "";
        this.lastMatchedKind = "";
        this.exitLabelStack.clear();
        this.loopStartLabelStack.clear();

        // Запуск разбора со стартового нетерминала
        parseNonTerminal("A"); // стартовый символ грамматики

        if (!currentSymbol().equals(END)) {
            throw new ParseException("Лишние токены после разбора, встретилось '" + currentSymbol() + "'");
        }

        return new Result(currentOpsString(), List.copyOf(opsOutput));
    }

    private void parseNonTerminal(String nonTerminal) {
        recursionDepth++;
        String opsBefore = currentOpsString();
        try {
            String lookahead = currentSymbol();
            GreibachGrammar.Rule rule = chooseRule(nonTerminal, lookahead);
            if (rule == null) {
                throw new ParseException("Не удалось подобрать правило для " + nonTerminal + " при lookahead '" + lookahead + "'");
            }

            List<String> symbols = rule.symbols();
            List<String> ops = rule.ops();
            int max = symbols.size();
            for (int i = 0; i < max; i++) {
                String sym = symbols.get(i);
                String op = i < ops.size() ? ops.get(i) : "";

                // Последовательно обрабатываем символы правой части
                if (!EPSILON.equals(sym)) {
                    if (nonTerminals.contains(sym)) {
                        parseNonTerminal(sym);
                    } else {
                        match(sym);
                    }
                }

                // Выполняем семантическое действие, если оно описано
                if (op != null && !op.isBlank() && !"□".equals(op)) {
                    opsOutput.addAll(resolveOp(op));
                }
            }
            logIteration(nonTerminal, rule, opsBefore, currentOpsString());
        } finally {
            recursionDepth--;
        }
    }

    private void match(String terminal) {
        String lookahead = currentSymbol();
        if (!terminal.equals(lookahead)) {
            throw new ParseException("Ожидалось '" + terminal + "', встретилось '" + lookahead + "'");
        }
        Lexer.Token token = inputTokens.get(position);
        this.lastMatchedLexeme = token.lexeme();
        this.lastMatchedKind = switch (token.type()) {
            case IDENTIFIER -> "identifier";
            case NUMBER -> "number";
            case KEYWORD -> "keyword";
            case OPERATOR -> "operator";
        };
        position++;
    }

    private GreibachGrammar.Rule chooseRule(String nonTerminal, String lookahead) {
        // Ищем первое правило, согласованное с текущим lookahead по FIRST/FOLLOW
        for (GreibachGrammar.Rule rule : grammar.getRules(nonTerminal)) {
            Set<String> first = firstOfSequence(rule.symbols());
            if (first.contains(lookahead)) {
                return rule;
            }
            if (first.contains(EPSILON) && follow(nonTerminal).contains(lookahead)) {
                return rule;
            }
        }
        return null;
    }

    private String currentSymbol() {
        if (position >= inputSymbols.size()) {
            return END;
        }
        return inputSymbols.get(position);
    }

    private Set<String> first(String symbol) {
        if (firstCache.containsKey(symbol)) {
            return firstCache.get(symbol);
        }

        Set<String> result = new HashSet<>();
        if (!nonTerminals.contains(symbol)) {
            result.add(symbol);
            firstCache.put(symbol, result);
            return result;
        }

        for (GreibachGrammar.Rule rule : grammar.getRules(symbol)) {
            Set<String> seqFirst = firstOfSequence(rule.symbols());
            result.addAll(seqFirst);
        }

        firstCache.put(symbol, result);
        return result;
    }

    private Set<String> firstOfSequence(List<String> sequence) {
        Set<String> result = new HashSet<>();
        boolean allNullable = true;
        for (String sym : sequence) {
            Set<String> symFirst = first(sym);
            result.addAll(withoutEpsilon(symFirst));
            if (!symFirst.contains(EPSILON)) {
                allNullable = false;
                break;
            }
        }
        if (allNullable) {
            result.add(EPSILON);
        }
        return result;
    }

    private Set<String> follow(String nonTerminal) {
        return followCache.get(nonTerminal);
    }

    private void initializeFollow() {
        for (String nt : nonTerminals) {
            followCache.put(nt, new HashSet<>());
        }
        followCache.get("A").add(END); // стартовый символ

        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, List<GreibachGrammar.Rule>> entry : grammar.allRules().entrySet()) {
                String lhs = entry.getKey();
                for (GreibachGrammar.Rule rule : entry.getValue()) {
                    List<String> symbols = rule.symbols();
                    if (symbols.size() == 1 && EPSILON.equals(symbols.get(0))) {
                        continue;
                    }
                    // Идем справа налево, накапливая FOLLOW через трейлер
                    Set<String> trailer = new HashSet<>(followCache.get(lhs));
                    for (int i = symbols.size() - 1; i >= 0; i--) {
                        String sym = symbols.get(i);
                        if (nonTerminals.contains(sym)) {
                            Set<String> followSet = followCache.get(sym);
                            if (followSet.addAll(trailer)) {
                                changed = true;
                            }
                            Set<String> firstSym = first(sym);
                            if (firstSym.contains(EPSILON)) {
                                trailer.addAll(withoutEpsilon(firstSym));
                            } else {
                                trailer = new HashSet<>(withoutEpsilon(firstSym));
                            }
                        } else {
                            trailer = new HashSet<>();
                            trailer.add(sym);
                        }
                    }
                }
            }
        } while (changed);
    }

    private Set<String> collectTerminals() {
        Set<String> terms = new HashSet<>();
        for (List<GreibachGrammar.Rule> rules : grammar.allRules().values()) {
            for (GreibachGrammar.Rule rule : rules) {
                for (String sym : rule.symbols()) {
                    if (!nonTerminals.contains(sym) && !EPSILON.equals(sym)) {
                        terms.add(sym);
                    }
                }
            }
        }
        terms.add(END);
        return terms;
    }

    private List<OpsElement> resolveOp(String op) {
        List<OpsElement> produced = new ArrayList<>();

        switch (op) {
            case "a" -> {
                produced.add(new OpsElement(lastMatchedLexeme, "identifier"));
            }
            case "k" -> {
                produced.add(new OpsElement(lastMatchedLexeme, "number"));
            }
            case ":" -> {
                pendingAssignOp = true; // ждем "=" чтобы склеить в ":="
            }
            case "=" -> {
                if (pendingAssignOp) {
                    produced.add(new OpsElement(":=", "operation"));
                    pendingAssignOp = false;
                } else {
                    produced.add(new OpsElement("=", "operation"));
                }
            }
            case "7" -> {
                int placeholderPos = opsOutput.size();
                exitLabelStack.push(placeholderPos);
                produced.add(new OpsElement("M?", "label-placeholder"));
                produced.add(new OpsElement("jf", "operation"));
            }
            case "8" -> {
                if (exitLabelStack.isEmpty()) {
                    throw new ParseException("Пустой стек меток при выполнении семантики 8");
                }
                int placeholderPos = exitLabelStack.pop();
                int target = opsOutput.size();
                opsOutput.set(placeholderPos, new OpsElement("M" + target, "label"));
            }
            case "9" -> {
                int startPos = opsOutput.size();
                loopStartLabelStack.push(startPos);
            }
            case "10" -> {
                if (loopStartLabelStack.isEmpty() || exitLabelStack.isEmpty()) {
                    throw new ParseException("Пустой стек начала цикла при выполнении семантики 10");
                }
                int startPos = loopStartLabelStack.pop();
                int exitPlaceholderPos = exitLabelStack.pop();
                int exitTarget = opsOutput.size() + 2; // после добавления метки возврата и j
                opsOutput.set(exitPlaceholderPos, new OpsElement("M" + exitTarget, "label"));
                produced.add(new OpsElement("M" + startPos, "label"));
                produced.add(new OpsElement("j", "operation"));
            }
            default -> produced.add(new OpsElement(op, "operation"));
        }

        return produced;
    }

    private static Set<String> withoutEpsilon(Set<String> set) {
        Set<String> copy = new HashSet<>(set);
        copy.remove(EPSILON);
        return copy;
    }

    private String currentOpsString() {
        return opsOutput.stream()
                .map(OpsElement::value)
                .collect(Collectors.joining(" "));
    }

    private void logIteration(String nonTerminal, GreibachGrammar.Rule rule, String opsBefore, String opsAfter) {
        if (!loggingEnabled) {
            return;
        }
        String indent = "  ".repeat(Math.max(0, recursionDepth - 1));
        String symbols = String.join(" ", rule.symbols());
        String ops = rule.ops().isEmpty() ? "" : " | OPS: " + String.join(" ", rule.ops());
        System.out.println(indent + "Применяем: " + nonTerminal + " -> " + symbols + ops);
        System.out.println(indent + "ОПС до:     " + opsBefore);
        System.out.println(indent + "ОПС после:  " + opsAfter);
    }

    private String mapTokenToTerminal(Lexer.Token token) {
        return switch (token.type()) {
            case KEYWORD -> token.lexeme().toUpperCase();
            case OPERATOR -> token.lexeme();
            case NUMBER -> "k";
            case IDENTIFIER -> "a";
        };
    }
}
