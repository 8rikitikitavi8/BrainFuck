import java.util.Arrays;
import java.util.Scanner;


public class BrainFuck {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        run("+++[->>>>+<<<<>++[->+<]]+", scanner); // Эхо
    }

    static void run(String code,Scanner scanner) {

        char[] commands = code.toCharArray();
        char[] memory = new char[10];
        int memoryPointer = 0;

        for (int commandPointer = 0; commandPointer < commands.length; commandPointer++) {
            if (commands[commandPointer] == '+') {
                memory[memoryPointer]++;
            }
            if (commands[commandPointer] == '-') {
                memory[memoryPointer]--;
            }
            if (commands[commandPointer] == '>') {
                memoryPointer++;
            }
            if (commands[commandPointer] == '<') {
                memoryPointer--;
            }
            if (commands[commandPointer] == '.') {
                System.out.print(memory[memoryPointer]);
            }
            if (commands[commandPointer] == ',') {
                memory[memoryPointer] = (char) scanner.nextInt();
            }
            if (commands[commandPointer] == '[') {
                if (memory[memoryPointer] == 0) {
                    int brc = 0;
                    ++brc;
                    while (brc > 0) {
                        ++commandPointer;
                        if (commandPointer >= commands.length) {
                            break;
                        }
                        if (commands[commandPointer] == '[')
                            ++brc;
                        if (commands[commandPointer] == ']') {
                            --brc;;
                        }
                    }
                }
            } else if (commands[commandPointer] == ']') {
                if (memory[memoryPointer] == 0) {
                    continue;
                } else {
                    int brc = 1;
                    while (brc > 0) {
                        --commandPointer;
                        if (commandPointer < 0) {
                            break;
                        }
                        if (commands[commandPointer] == '[') {
                            brc--;
                        }
                        if (commands[commandPointer] == ']') {
                            brc++;
                        }
                    }
                }
            }
        }
    }

    static void show(char[] array) {
        System.out.println(Arrays.toString(new String(array).getBytes()));
    }
}

