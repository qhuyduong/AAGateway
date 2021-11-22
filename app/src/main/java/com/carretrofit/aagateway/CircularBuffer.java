package com.carretrofit.aagateway;

import java.io.*;

class CircularBuffer {
    // Initial Capacity of Buffer
    private int capacity = 0;
    // Initial Size of Buffer
    private int size = 0;
    // Head pointer
    private int head = 0;
    // Tail pointer
    private int tail = -1;
    private Object[] array;

    // Constructor
    CircularBuffer(int capacity) {
        // Initializing the capacity of the array
        this.capacity = capacity;

        // Initializing the array
        array = new Object[capacity];
    }

    // Addition of elements
    public void add(Object element) throws Exception {

        // Calculating the index to add the element
        int index = (tail + 1) % capacity;

        // Size of the array increases as elements are added
        size++;

        // Checking if the array is full
        if (size == capacity) {
            throw new Exception("Buffer Overflow");
        }

        // Storing the element in the array
        array[index] = element;

        // Incrementing the tail pointer to point
        // to the element added currently
        tail++;
    }

    // Deletion of elements
    public Object get() throws Exception {

        // Checking if the array is empty
        if (size == 0) {
            throw new Exception("Empty Buffer");
        }

        // Calculating the index of the element to be
        // deleted
        int index = head % capacity;

        // Getting the element
        Object element = array[index];

        // Incrementing the head pointer to point
        // to the next element
        head++;

        // Decrementing the size of the array as the
        // elements are deleted
        size--;

        // Returning the first element
        return element;
    }

    // Retrieving the first element without deleting it
    public Object peek() throws Exception {

        // Checking if the array is empty
        if (size == 0) {
            throw new Exception("Empty Buffer");
        }

        // Calculating the index of the
        // element to be deleted
        int index = head % capacity;

        // Getting the element
        Object element = array[index];

        // Returning the element
        return element;
    }

    // Checking if the array is empty
    public boolean isEmpty() {
        return size == 0;
    }

    // Size of the array
    public int size() {
        return size;
    }
}
