package LearningStreams;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class L1SimpleStreams {

    public static void main(String[] args) {
        Stream<Integer> stream = IntStream.range(0, 10_000).boxed();

        Stream<Integer> fiveDivIntStream = stream.filter(i -> i % 5 == 0);
//        Stream<Integer> evenIntStream = fiveDivIntStream.map(i->i*i);
        fiveDivIntStream.forEach(System.out::println);
        fiveDivIntStream.forEach(System.out::println);
//        List<Integer> list = evenIntStream.collect(Collectors.toList());
//        list.forEach(System.out::println);
    }
}
