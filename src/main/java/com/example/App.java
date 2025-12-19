package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Пример запуска лексического анализатора.
 */
public class App {

    public static void main(String[] args) {
        boolean quiet = false;
        Path programPath = Path.of("program_test.txt");
        if (args.length > 0) {
            int idx = 0;
            if (!args[0].startsWith("-")) {
                programPath = Path.of(args[0]);
                idx = 1;
            }
            for (int i = idx; i < args.length; i++) {
                if ("--quiet".equalsIgnoreCase(args[i]) || "-q".equalsIgnoreCase(args[i])) {
                    quiet = true;
                }
            }
        }
        try {
            String source = Files.readString(programPath);
            Lexer lexer = new Lexer();
            GreibachGrammar grammar = new GreibachGrammar();
            Parser parser = new Parser(grammar, lexer);
            parser.setLoggingEnabled(!quiet);
            Parser.Result result = parser.parse(source);
            OpsExecutor executor = new OpsExecutor();
            OpsExecutor.ExecResult execResult = executor.execute(result.opsSequence());

            if (!quiet) {
                System.out.println("Исходный код:");
                System.out.println(source);
                System.out.println("\nОПС:");
                System.out.println(result.opsString());
                System.out.println("\nОПС (значение, тип):");
                for (Parser.OpsElement el : result.opsSequence()) {
                    System.out.println(el.value() + " : " + el.type());
                }
                System.out.println("\nРезультаты выполнения:");
            } else {
                System.out.println("Результаты выполнения:");
            }
            if (execResult.output().isEmpty()) {
                System.out.println("(вывода нет)");
            } else {
                for (String line : execResult.output()) {
                    System.out.println(line);
                }
            }
            System.out.println("\nСостояние переменных:");
            execResult.variables().forEach((k, v) -> System.out.println(k + " = " + v));
        } catch (IOException e) {
            System.err.println("Не удалось прочитать файл " + programPath + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка лексического анализа: " + e.getMessage());
        } catch (Parser.ParseException e) {
            System.err.println("Ошибка синтаксического анализа: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Ошибка выполнения ОПС: " + e.getMessage());
        }
    }
}
