package org.bybittradeapp.service;
import java.util.ArrayList;
import java.util.List;

public class SidewaysRangeDetector {

    // Класс для хранения зон
    public static class Range {
        public double low;
        public double high;
        public int startIndex;
        public int endIndex;

        public Range(double low, double high, int startIndex, int endIndex) {
            this.low = low;
            this.high = high;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    public static List<Range> detectSidewaysRanges(double[] prices, int windowSize, double stdDevThreshold) {
        List<Range> ranges = new ArrayList<>();

        for (int i = 0; i < prices.length - windowSize; i++) {
            double[] window = new double[windowSize];
            System.arraycopy(prices, i, window, 0, windowSize);

            double average = calculateAverage(window);
            double stdDev = calculateStandardDeviation(window, average);

            if (stdDev < stdDevThreshold) {
                double low = findMin(window);
                double high = findMax(window);
                ranges.add(new Range(low, high, i, i + windowSize - 1));
            }
        }
        return ranges;
    }

    private static double calculateAverage(double[] data) {
        double sum = 0.0;
        for (double d : data) {
            sum += d;
        }
        return sum / data.length;
    }

    private static double calculateStandardDeviation(double[] data, double mean) {
        double sum = 0.0;
        for (double d : data) {
            sum += Math.pow(d - mean, 2);
        }
        return Math.sqrt(sum / data.length);
    }

    private static double findMin(double[] data) {
        double min = data[0];
        for (double d : data) {
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    private static double findMax(double[] data) {
        double max = data[0];
        for (double d : data) {
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    public static void main(String[] args) {
        // Пример данных цен
        double[] prices = null; // Здесь нужно указать цены BTCUSDT
        int windowSize = 20; // Окно для анализа
        double stdDevThreshold = 50.0; // Порог стандартного отклонения

        List<Range> sidewaysRanges = detectSidewaysRanges(prices, windowSize, stdDevThreshold);

        for (Range range : sidewaysRanges) {
            System.out.println("Range detected from index " + range.startIndex + " to " + range.endIndex +
                    " with low " + range.low + " and high " + range.high);
        }
    }
}
