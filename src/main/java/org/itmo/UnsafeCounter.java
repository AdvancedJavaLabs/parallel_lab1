package org.itmo;

public class UnsafeCounter {
    private int counter = 0;

    public void increment() {
        counter++; // <-- гонка данных
    }

    public int get() {
        return counter;
    }
}
