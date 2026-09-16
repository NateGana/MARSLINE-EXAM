package com.marsline.eip;

/**
 * Entry point for the whole project.
 *
 * This is what runs when you type:
 *   mvn compile exec:java -Dexec.args="task1"
 *   mvn compile exec:java -Dexec.args="task2"
 *   mvn compile exec:java -Dexec.args="task3"
 *   mvn compile exec:java -Dexec.args="task4"
 *   mvn compile exec:java -Dexec.args="task5"
 *
 * It just reads the first argument and calls the matching task's run() method.
 * Each task is fully self-contained in its own class/file.
 */
public class MarslineEipApplication {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("MARSLINE EIP - ITP103 Midterm Lab Exam");
            System.out.println("Please choose a task to run, for example:");
            System.out.println("  mvn compile exec:java -Dexec.args=\"task1\"");
            System.out.println("Valid options: task1, task2, task3, task4, task5");
            return;
        }

        String selected = args[0].trim().toLowerCase();

        System.out.println("ITP103 MIDTERM LAB EXAM | GROUP MARSLINE");
        System.out.println("Enterprise Integration Patterns - MARSLINE Busline (Cabuyao, Laguna)");
        System.out.println("Selected task: " + selected);

        switch (selected) {
            case "task1":
                new Task1MessageChannel().run();
                break;
            case "task2":
                new Task2ContentBasedRouter().run();
                break;
            case "task3":
                new Task3Aggregator().run();
                break;
            case "task4":
                new Task4MessageTranslator().run();
                break;
            case "task5":
                new Task5ErrorChannelRetry().run();
                break;
            default:
                System.out.println("Unknown task: " + selected);
                System.out.println("Valid options: task1, task2, task3, task4, task5");
        }
    }
}
