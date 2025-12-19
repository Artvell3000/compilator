package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Представление грейбаховской грамматики.
 * Хранит правила в виде словаря: нетерминал -> список правил.
 * Каждое правило содержит последовательность выводимых символов и ОПС.
 */
public final class GreibachGrammar {

    /**
     * Правило грамматики: правая часть (список символов) и ОПС (также список символов).
     */
    public record Rule(List<String> symbols, List<String> ops, String comment) {
        public Rule {
            symbols = List.copyOf(symbols);
            ops = List.copyOf(ops);
            comment = comment == null ? "" : comment;
        }
    }

    private static final Map<String, List<Rule>> DEFAULT_RULES = createDefaultRules();

    private final Map<String, List<Rule>> rules;

    public GreibachGrammar() {
        this(DEFAULT_RULES);
    }

    private GreibachGrammar(Map<String, List<Rule>> rules) {
        this.rules = rules;
    }

    /**
     * Возвращает правила для указанного нетерминала.
     */
    public List<Rule> getRules(String nonTerminal) {
        return rules.getOrDefault(nonTerminal, List.of());
    }

    /**
     * Все правила грамматики.
     */
    public Map<String, List<Rule>> allRules() {
        return Collections.unmodifiableMap(rules);
    }

    private static Map<String, List<Rule>> createDefaultRules() {
        Map<String, List<Rule>> grammar = new LinkedHashMap<>();

        add(grammar, "A", List.of("VAR", "P", ";", "A"), List.of("□", "□", "□", "□"), "Объявление переменной");
        add(grammar, "A", List.of("ARRAY", "K", ";", "A"), List.of("□", "□", "□", "□"), "Объявление массива");
        add(grammar, "A", List.of("IF", "(", "L", ")", "THEN", "{", "A", "}", "C", "Z", ";", "A"),
                List.of("□", "□", "□", "□", "7", "□", "□", "□", "□", "8", "□", "□"), "Условная конструкция");
        add(grammar, "A", List.of("WHILE", "(", "L", ")", "DO", "{", "A", "}", "Z", ";", "A"),
                List.of("9", "□", "□", "□", "7", "□", "□", "□", "10", "□", "□"), "Цикл WHILE");
        add(grammar, "A", List.of("a", "H", ":=", "E", "Z", ";", "A"),
                List.of("a", "□", "□", "□", ":", "=", "□", "□"), "Присвоение значения переменной");
        add(grammar, "A", List.of("OUTPUT", "E", ";", "A"), List.of("□", "□", "o", "□"), "Вывод программы");
        add(grammar, "A", List.of("INPUT", "I'", ";", "A"), List.of("□", "□", "s", "□"), "Ввод значения переменной");
        add(grammar, "A", List.of("λ"), List.of(), "Конец программы");

        add(grammar, "C", List.of("ELSE", "{", "A", "}"), List.of("2", "□", "□", "□"),
                "Условная конструкция: альтернативные действия");
        add(grammar, "C", List.of("λ"), List.of(), "Условная конструкция: конец");

        add(grammar, "H", List.of("[", "E", "]"), List.of("□", "□", "i"), "Индекс массива");
        add(grammar, "H", List.of("λ"), List.of(), "Обычная переменная");

        add(grammar, "E", List.of("-", "G", "V", "U"), List.of("□", "□", "-'", "□"), "");
        add(grammar, "E", List.of("(", "E", ")", "V", "U"), List.of("□", "□", "□", "□", "□"), "");
        add(grammar, "E", List.of("a", "H", "V", "U"), List.of("a", "□", "□", "□"), "");
        add(grammar, "E", List.of("k", "V", "U"), List.of("k", "□", "□"), "");

        add(grammar, "U", List.of("+", "T", "U"), List.of("□", "□", "+"), "");
        add(grammar, "U", List.of("-", "T", "U"), List.of("□", "□", "-"), "");
        add(grammar, "U", List.of("λ"), List.of(), "");

        add(grammar, "V", List.of("*", "F", "V"), List.of("□", "□", "*"), "");
        add(grammar, "V", List.of("/", "F", "V"), List.of("□", "□", "/"), "");
        add(grammar, "V", List.of("λ"), List.of(), "");

        add(grammar, "T", List.of("-", "G", "V"), List.of("□", "□", "-'"), "");
        add(grammar, "T", List.of("(", "E", ")", "V"), List.of("□", "□", "□", "□"), "");
        add(grammar, "T", List.of("a", "H", "V"), List.of("a", "□", "□"), "");
        add(grammar, "T", List.of("k", "V"), List.of("k", "□"), "");

        add(grammar, "F", List.of("-", "G", "Z"), List.of("□", "□", "-'"), "");
        add(grammar, "F", List.of("(", "E", ")"), List.of("□", "□", "□"), "");
        add(grammar, "F", List.of("a", "H"), List.of("a", "□"), "");
        add(grammar, "F", List.of("k"), List.of("k"), "");

        add(grammar, "G", List.of("(", "E", ")"), List.of("□", "□", "□"), "");
        add(grammar, "G", List.of("a", "H"), List.of("a", "□"), "");
        add(grammar, "G", List.of("k"), List.of("k"), "");

        add(grammar, "L", List.of("-", "G", "Z", "U", "O", "X", "W"), List.of("□", "□", "-'", "□", "□", "□", "□"), "");
        add(grammar, "L", List.of("(", "L", ")", "X", "W"), List.of("□", "□", "□", "□", "□"), "");
        add(grammar, "L", List.of("a", "H", "V", "U", "O", "X", "W"), List.of("a", "□", "□", "□", "□", "□", "□"), "");
        add(grammar, "L", List.of("k", "V", "U", "O", "X", "W"), List.of("k", "□", "□", "□", "□", "□"), "");
        add(grammar, "L", List.of("!", "(", "L", ")", "X", "W"), List.of("□", "□", "□", "□", "□", "!"), "");

        add(grammar, "M", List.of("-", "G", "Z", "V", "U", "O", "X"), List.of("□", "□", "-'", "□", "□", "□", "□"), "");
        add(grammar, "M", List.of("(", "L", ")", "X"), List.of("□", "□", "□", "□"), "");
        add(grammar, "M", List.of("a", "H", "V", "U", "O", "X"), List.of("a", "□", "□", "□", "□", "□"), "");
        add(grammar, "M", List.of("k", "V", "U", "O", "X"), List.of("k", "□", "□", "□"), "");
        add(grammar, "M", List.of("!", "(", "L", ")", "X"), List.of("□", "□", "□", "□", "!"), "");

        add(grammar, "W", List.of("OR", "M", "W"), List.of("□", "□", "OR"), "");
        add(grammar, "W", List.of("λ"), List.of(), "");

        add(grammar, "X", List.of("AND", "N", "X"), List.of("□", "□", "AND"), "");
        add(grammar, "X", List.of("λ"), List.of(), "");

        add(grammar, "N", List.of("-", "G", "Z", "V", "U", "O"), List.of("□", "□", "-'", "□", "□", "□"), "");
        add(grammar, "N", List.of("(", "L", ")"), List.of("□", "□", "□"), "");
        add(grammar, "N", List.of("a", "H", "V", "U", "O"), List.of("a", "□", "□", "□", "□"), "");
        add(grammar, "N", List.of("k", "V", "U", "O"), List.of("k", "□", "□", "□"), "");
        add(grammar, "N", List.of("!", "(", "L", ")", "Z"), List.of("□", "□", "□", "□", "!"), "");

        add(grammar, "O", List.of("<", "E", "Z"), List.of("□", "□", "<"), "");
        add(grammar, "O", List.of(">", "E", "Z"), List.of("□", "□", ">"), "");
        add(grammar, "O", List.of(">=", "E", "Z"), List.of("□", "□", ">="), "");
        add(grammar, "O", List.of("<=", "E", "Z"), List.of("□", "□", "<="), "");
        add(grammar, "O", List.of("==", "E", "Z"), List.of("□", "□", "=="), "");
        add(grammar, "O", List.of("!=", "E", "Z"), List.of("□", "□", "!="), "");

        add(grammar, "P", List.of("a", "Z", "B"), List.of("a", "n", "□"), "");
        add(grammar, "B", List.of(":=", "E", "Z"), List.of("□", "□", "f"), "");
        add(grammar, "B", List.of("λ"), List.of(), "");

        add(grammar, "K", List.of("a", "R"), List.of("a", "□"), "");
        add(grammar, "R", List.of("(", "E", ")"), List.of("□", "□", "ar"), "");
        add(grammar, "I'", List.of("a", "H"), List.of("a", "□"), "");
        add(grammar, "Z", List.of("λ"), List.of(), "");

        Map<String, List<Rule>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<Rule>> entry : grammar.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static void add(Map<String, List<Rule>> grammar, String nonTerminal, List<String> symbols, List<String> ops, String comment) {
        Rule rule = new Rule(symbols, ops, comment);
        grammar.computeIfAbsent(nonTerminal, key -> new ArrayList<>()).add(rule);
    }


    public static void main(String[] args) {
        GreibachGrammar grammar = new GreibachGrammar();
        for (Map.Entry<String, List<Rule>> entry : grammar.allRules().entrySet()) {
            String nonTerminal = entry.getKey();
            for (Rule rule : entry.getValue()) {
                String symbols = String.join(" ", rule.symbols());
                String ops = rule.ops().isEmpty() ? "" : " | OPS: " + String.join(" ", rule.ops());
                String comment = rule.comment().isEmpty() ? "" : " | " + rule.comment();
                System.out.println(nonTerminal + " -> " + symbols + ops + comment);
            }
        }
    }
}
