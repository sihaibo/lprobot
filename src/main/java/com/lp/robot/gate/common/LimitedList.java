package com.lp.robot.gate.common;

import java.util.LinkedList;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-17 17:52<br/>
 * @since JDK 1.8
 */
public class LimitedList<E> extends LinkedList<E> {

    private int limit;

    public LimitedList(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E e) {
        super.add(e);
        while (size() > limit) {super.remove();}
        return true;
    }

    public static void main(String[] args) {
        LimitedList<String> limitedList = new LimitedList<>(3);
        for (int i = 0; i < 5; i++) {
            limitedList.add("" + i);
        }
        System.out.println(limitedList);
    }
}