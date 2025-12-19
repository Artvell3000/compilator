package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Лексический анализатор для языка из файла "Лексемы.html".
 * Возвращает список токенов, где каждый токен содержит исходный текстовый вид
 * и его лексический смысл (комментарий из таблицы).
 */
public class Lexer {

    public enum TokenType {
        KEYWORD,
        OPERATOR,
        NUMBER,
        IDENTIFIER
    }

    /**
     * Упрощенный Pair без внешних зависимостей.
     */
    public record Pair<A, B>(A first, B second) {}

    /**
     * Токен со значением и типом.
     */
    public record Token(String lexeme, TokenType type) {}

    private static final Set<String> KEYWORDS = Set.of(
            "VAR", "ARRAY", "INPUT", "IF", "THEN", "ELSE", "WHILE", "DO", "OUTPUT", "AND", "OR"
    );

    private static final Set<String> OPERATORS = Set.of(
            ":=", "+", "-", "*", "/", "<", ">", ">=", "<=", "==", "!=", "(", ")", "{", "}", "[", "]", ";", "!"
    );

    /**
     * Разбивает исходный код на токены.
     *
     * @param source исходная строка программы
     * @return список токенов (lexeme + meaning)
     */
    public List<Token> tokenize(String source) {
        Objects.requireNonNull(source, "source");
        List<Token> result = new ArrayList<>();
        int i = 0;
        int length = source.length();

        while (i < length) {
            char ch = source.charAt(i);

            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            // Многосимвольные операторы проверяем первыми.
            String twoChars = (i + 1 < length) ? source.substring(i, i + 2) : null;
            if (twoChars != null && OPERATORS.contains(twoChars)) {
                result.add(new Token(twoChars, TokenType.OPERATOR));
                i += 2;
                continue;
            }

            // Односивольные операторы/разделители.
            String oneChar = String.valueOf(ch);
            if (OPERATORS.contains(oneChar)) {
                result.add(new Token(oneChar, TokenType.OPERATOR));
                i++;
                continue;
            }

            // Идентификаторы или ключевые слова.
            if (Character.isLetter(ch) || ch == '_') {
                int start = i;
                i++;
                while (i < length) {
                    char c = source.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                String lexeme = source.substring(start, i);
                String lookupKey = lexeme.toUpperCase();
                if (KEYWORDS.contains(lookupKey)) {
                    result.add(new Token(lexeme, TokenType.KEYWORD));
                } else {
                    result.add(new Token(lexeme, TokenType.IDENTIFIER));
                }
                continue;
            }

            // Числовая константа.
            if (Character.isDigit(ch)) {
                int start = i;
                i++;
                while (i < length && Character.isDigit(source.charAt(i))) {
                    i++;
                }
                String lexeme = source.substring(start, i);
                result.add(new Token(lexeme, TokenType.NUMBER));
                continue;
            }

            throw new IllegalArgumentException("Неизвестный символ '" + ch + "' на позиции " + i);
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Удобный вспомогательный метод, если хочется напрямую получить пары
     * (значение, лексический смысл) без Token.
     */
    public List<Pair<String, String>> tokenizeAsPairs(String source) {
        List<Token> tokens = tokenize(source);
        List<Pair<String, String>> pairs = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            pairs.add(new Pair<>(token.lexeme(), token.type().name()));
        }
        return Collections.unmodifiableList(pairs);
    }

    public static void main(String[] args) {
        String sample = "VAR a := 10; IF a >= 5 THEN OUTPUT a;";
        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.tokenize(sample);
        for (Token token : tokens) {
            System.out.println(token.lexeme() + " -> " + token.type());
        }
    }
}
