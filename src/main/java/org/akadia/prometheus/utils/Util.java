package org.akadia.prometheus.utils;

import io.prometheus.client.Collector;

import java.util.ArrayList;
import java.util.List;

public class Util {
    private final static String COMMON_PREFIX = "bungee_";

    public static String prefix(String name) {
        return COMMON_PREFIX + name;
    }

    public static List<Collector.MetricFamilySamples> prefixFromCollector(Collector collector) {
        List<Collector.MetricFamilySamples> collected = collector.collect();
        List<Collector.MetricFamilySamples> mfs = new ArrayList<>();

        for (Collector.MetricFamilySamples mSample : collected) {
            List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>(mSample.samples.size());
            for (Collector.MetricFamilySamples.Sample sample : mSample.samples) {
                samples.add(new Collector.MetricFamilySamples.Sample(Util.prefix(sample.name), sample.labelNames, sample.labelValues, sample.value));
            }

            Collector.MetricFamilySamples prefixed = new Collector.MetricFamilySamples(Util.prefix(mSample.name), mSample.type, mSample.help, samples);
            mfs.add(prefixed);
        }
        return mfs;
    }
}
