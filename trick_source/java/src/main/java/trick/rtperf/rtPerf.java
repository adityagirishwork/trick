package main.java.trick.rtperf;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.Math;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

class JobExecutionEvent {
    public String id;
    public boolean isEOF;
    public boolean isTOF;
    public double start;
    public double stop;

    public JobExecutionEvent(String identifier, boolean isTopOfFrame, boolean isEndOfFrame, double start_time, double stop_time) {
        id = identifier;
        isEOF = isEndOfFrame;
        isTOF = isTopOfFrame;
        start = start_time;
        stop = stop_time;
    }

    public String toString() {
        return ( "JobExecutionEvent: " + id + "," + start + "," + stop );
    }
}