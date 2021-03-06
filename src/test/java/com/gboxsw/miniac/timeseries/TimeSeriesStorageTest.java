package com.gboxsw.miniac.timeseries;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.*;

import com.gboxsw.miniac.timeseries.TimeSeriesStorage.SampleReader;

public class TimeSeriesStorageTest {

    /**
     * Numbef of record used for testing using large data sets.
     */
    private static final int LARGE_TEST_RECORD_COUNT = 5_000;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testNullValues() throws IOException {
        File testFolder = folder.newFolder();
        File storageFile = new File(testFolder, "storage.tss");

        Map<String, Integer> items = new HashMap<>();
        items.put("prop1", 0);
        items.put("prop2", 1);
        TimeSeriesStorage.create(storageFile, items, 4000);

        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        Map<String, Number> sample;
        List<Map<String, Number>> samples = new ArrayList<>();

        sample = storage.createEmptySample();
        sample.put("prop1", 1L);
        sample.put("prop2", 2.8d);
        samples.add(new HashMap<>(sample));
        storage.addSample(0, sample);

        sample = storage.createEmptySample();
        sample.put("prop2", 3.6d);
        samples.add(new HashMap<>(sample));
        storage.addSample(1, sample);

        sample = storage.createEmptySample();
        sample.put("prop1", 12L);
        samples.add(new HashMap<>(sample));
        storage.addSample(2, sample);

        sample = storage.createEmptySample();
        samples.add(new HashMap<>(sample));
        storage.addSample(3, sample);

        sample = storage.createEmptySample();
        sample.put("prop1", 5L);
        sample.put("prop2", 9.3d);
        samples.add(new HashMap<>(sample));
        storage.addSample(4, sample);

        storage.flush();

        SampleReader reader = storage.createReader(0);
        int idx = 0;
        while (reader.hasNext()) {
            reader.next();
            assertEquals("Read and written time should be the same.", idx, reader.getTime());

            Map<String, Number> expectedValues = samples.get(idx);
            Map<String, Number> values = reader.getValues();
            for (String key : expectedValues.keySet()) {
                assertEquals("Read and written item values should be the same.", expectedValues.get(key),
                        values.get(key));
            }
            idx++;
        }
        assertEquals("All items have been read.", samples.size(), idx);
        reader.close();
    }

    @Test
    public void testUnflushedReading() throws IOException {
        File testFolder = folder.newFolder();

        File storageFile = new File(testFolder, "storage.tss");
        File dataFile = new File(testFolder, "data.txt");
        createTestStorage(storageFile, dataFile, 30);

        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        storage.setAutoFlush(false);

        for (int i = 0; i < 10; i++) {
            Map<String, Number> sample = storage.createEmptySample();
            for (String key : sample.keySet()) {
                sample.put(key, Math.random() * 1000);
            }
            storage.addSample(storage.getTime() + 1 + (int) (Math.random() * 20), sample);
        }

        try (SampleReader reader = storage.createReader(0)) {
            int count = 0;
            while (reader.hasNext()) {
                reader.next();
                count++;
            }

            assertEquals("The total number of samples should be 30 + 10", 30 + 10, count);
        }
    }

    @Test
    public void testStorageTime() throws IOException {
        File testFolder = folder.newFolder();

        File storageFile = new File(testFolder, "storage.tss");
        File dataFile = new File(testFolder, "data.txt");
        long endTime = createTestStorage(storageFile, dataFile, 10_000);

        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        assertEquals("Time of storage should be the same as the time of the last stored sample.", endTime,
                storage.getTime());
    }

    @Test
    public void testLargeWriteAndRead() throws IOException {
        File testFolder = folder.newFolder();

        File storageFile = new File(testFolder, "storage.tss");
        File dataFile = new File(testFolder, "data.txt");
        createTestStorage(storageFile, dataFile, LARGE_TEST_RECORD_COUNT);

        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        compareData(storage, dataFile, 0);
    }

    @Test
    public void testLargeWriteAndIndexedRead() throws IOException {
        File testFolder = folder.newFolder();

        File storageFile = new File(testFolder, "storage.tss");
        File dataFile = new File(testFolder, "data.txt");
        long endTime = createTestStorage(storageFile, dataFile, LARGE_TEST_RECORD_COUNT);

        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        compareData(storage, dataFile, endTime / 2);
        compareData(storage, dataFile, endTime - 1);
        compareData(storage, dataFile, endTime);
    }

    /**
     * Compares data from reader with data stored in a data file.
     *
     * @param storage  the storage.
     * @param dataFile the file with data written to storage.
     * @param fromTime the time when reading of samples should start.
     */
    private void compareData(TimeSeriesStorage storage, File dataFile, long fromTime) throws IOException {
        SampleReader reader = storage.createReader(fromTime);
        try (Scanner scanner = new Scanner(dataFile)) {
            scanner.useLocale(Locale.US);
            while (scanner.hasNextLong()) {
                long time = scanner.nextLong();
                int prop1 = scanner.nextInt();
                double prop2 = scanner.nextDouble();
                double prop3 = scanner.nextDouble();
                // ignore old records
                if (time < fromTime) {
                    continue;
                }

                // read next sample
                reader.next();

                assertEquals("Time of read sample should be equal to written value", time, reader.getTime());

                Map<String, Number> values = reader.getValues();

                assertEquals("Prop1 of read sample should be equal to written value", prop1,
                        values.get("prop1").intValue());
                assertEquals("Prop2 of read sample should be equal to written value", prop2,
                        values.get("prop2").doubleValue(), 0);
                assertEquals("Prop3 of read sample should be equal to written value", prop3,
                        values.get("prop3").doubleValue(), 0);
            }
        }

        assertFalse("Reader should reach end of samples.", reader.hasNext());
        reader.close();
    }

    /**
     * Creates storage and fill it with random data.
     *
     * @param storageFile the name of the storage file.
     * @param dataFile    the name of the file that will contain randomly generated
     *                    data.
     * @param recordCount the number of generated samples.
     */
    private static long createTestStorage(File storageFile, File dataFile, int recordCount) throws IOException {
        // create and configure storage
        Map<String, Integer> items = new HashMap<>();
        items.put("prop1", 0);
        items.put("prop2", 1);
        items.put("prop3", 2);
        TimeSeriesStorage.create(storageFile, items, 500);

        // fill storage
        Random random = new Random();
        TimeSeriesStorage storage = new TimeSeriesStorage(storageFile);
        storage.setAutoFlush(true);

        long now = random.nextInt(1000);
        try (PrintWriter pw = new PrintWriter(dataFile)) {
            for (int i = 0; i < recordCount; i++) {
                now += 1 + random.nextInt(300);

                Map<String, Number> values = storage.createEmptySample();
                values.put("prop1", 50 - random.nextInt(100));
                values.put("prop2", (500 - random.nextInt(1000)) / 10.0);
                values.put("prop3", (5000 - random.nextInt(10000)) / 100.0);
                storage.addSample(now, values);

                pw.println(now + "\t" + values.get("prop1") + "\t" + values.get("prop2") + "\t" + values.get("prop3"));
            }

            storage.flush();
        }

        return now;
    }
}
