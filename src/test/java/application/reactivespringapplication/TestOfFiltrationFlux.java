package application.reactivespringapplication;

import lombok.Data;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestOfFiltrationFlux {
    @Test
    public void skipAFew() {
        //skip просто отбрасывает первые элементы
        Flux<String> countFlux = Flux.just(
                        "one", "two", "skip a few", "ninety nine", "one hundred")
                .skip(3);

        StepVerifier.create(countFlux)
                .expectNext("ninety nine", "one hundred")
                .verifyComplete();
    }

    @Test
    public void skipAFewSeconds() {
        Flux<String> countFlux = Flux.just(
                        "one", "two", "skip a few", "ninety nine", "one hundred")
                .delayElements(Duration.ofSeconds(1))
                //скип отбрасывает пока не закончится таймер, потом начинает публиковать
                .skip(Duration.ofSeconds(4));

        StepVerifier.create(countFlux)
                .expectNext("ninety nine", "one hundred")
                .verifyComplete();
    }

    @Test
    public void take() {
        Flux<String> nationalParkFlux = Flux.just(
                        "Yellowstone", "Yosemite", "Grand Canyon", "Zion", "Acadia")
                //take - это противоположность skip, наооброт берет первые несколько элеметнов
                .take(3);

        StepVerifier.create(nationalParkFlux)
                .expectNext("Yellowstone", "Yosemite", "Grand Canyon")
                .verifyComplete();
    }

    @Test
    public void takeForAwhile() {
        Flux<String> nationalParkFlux = Flux.just(
                        "Yellowstone", "Yosemite", "Grand Canyon", "Zion", "Grand Teton")
                .delayElements(Duration.ofSeconds(1))
                //тоже имеет вариант с таймером, передает элементы пока не истечет таймер
                .take(Duration.ofMillis(3500));

        StepVerifier.create(nationalParkFlux)
                .expectNext("Yellowstone", "Yosemite", "Grand Canyon")
                .verifyComplete();
    }

    @Test
    public void filter() {
        Flux<String> nationalParkFlux = Flux.just(
                        "Yellowstone", "Yosemite", "Grand Canyon", "Zion", "Grand Teton")
                //более общая операция фильтрации, получает предикат и проверяет его
                .filter(np -> !np.contains(" "));

        StepVerifier.create(nationalParkFlux)
                .expectNext("Yellowstone", "Yosemite", "Zion")
                .verifyComplete();
    }

    @Test
    public void distinct() {
        Flux<String> animalFlux = Flux.just(
                        "dog", "cat", "bird", "dog", "bird", "anteater")
                //фильтр, пропускающий только новые значения
                .distinct();

        StepVerifier.create(animalFlux)
                .expectNext("dog", "cat", "bird", "anteater")
                .verifyComplete();
    }

    @Data
    private static class Player {
        private final String firstName;
        private final String lastName;
    }

    @Test
    public void map() {
        Flux<Player> playerFlux = Flux
                .just("Michael Jordan", "Scottie Pippen", "Steve Kerr")
                //map преобразует одни объекты в другие через заданную в ней функцию
                //выполняет преобразование синхронно по мере поступления объектов из потока
                .map(n -> {
                    String[] split = n.split("\\s");
                    return new Player(split[0], split[1]);
                });
        StepVerifier.create(playerFlux)
                .expectNext(new Player("Michael", "Jordan"))
                .expectNext(new Player("Scottie", "Pippen"))
                .expectNext(new Player("Steve", "Kerr"))
                .verifyComplete();
    }

    @Test
    public void flatMap() {

        /**
         * Обратите внимание, что flatMap() принимает функцию (в этом
         * примере – лямбда-выражение), которая преобразует входную строку
         * в поток Mono типа String. Затем к потоку Mono применяется операция
         * map(), преобразующая строку в объект Player. После преобразования
         * строк в объекты Player во всех промежуточных потоках полученные
         * результаты публикуются в одном общем потоке, который возвращает
         * flatMap().
         * Если остановиться на этом, то полученный поток будет содержать объекты Player, созданные синхронно и в том же порядке, что
         * и в примере с map(). Но в нашем примере к потокам Mono применяется еще одна операция – subscribeOn(), чтобы указать, что каждая
         * подписка должна производиться в параллельном потоке выполнения.
         * Как следствие операции отображения нескольких входных объектов
         * String могут выполняться асинхронно и параллельно.
         * Несмотря на сходство имен subscribeOn() и subscribe(), это совершенно разные операции. Операция subscribe() – это глагол, означающий «подписаться на реактивный поток» и фактически запускающий
         * его, а операция subscribeOn() является скорее определением, описывающим, как должна осуществляться конкурентная подписка. Reactor
         * не навязывает какой-то определенной модели конкурентного выполнения; и именно через subscribeOn() можно указать модель, передав
         * один из статических методов класса Schedulers. В этом примере указан
         * метод parallel(), который использует рабочие потоки из фиксированного пула (размер этого пула соответствует количеству ядер процессора). Н
         */

        Flux<Player> playerFlux = Flux
                .just("Michael Jordan", "Scottie Pippen", "Steve Kerr")
                .flatMap(n -> Mono.just(n)
                        .map(p -> {
                            String[] split = p.split("\\s");
                            return new Player(split[0], split[1]);
                        })
                        .subscribeOn(Schedulers.parallel())
                );

        List<Player> playerList = Arrays.asList(
                new Player("Michael", "Jordan"),
                new Player("Scottie", "Pippen"),
                new Player("Steve", "Kerr"));

        //По итогу порядок отдачи элементов в финальнй поток не известен
        StepVerifier.create(playerFlux)
                .expectNextMatches(p -> playerList.contains(p))
                .expectNextMatches(p -> playerList.contains(p))
                .expectNextMatches(p -> playerList.contains(p))
                .verifyComplete();
    }

    @Test
    public void buffer() {
        Flux<String> fruitFlux = Flux.just(
                "apple", "orange", "banana", "kiwi", "strawberry");

        //разбивает на буферные структуры определенного максимального размера
        Flux<List<String>> bufferedFlux = fruitFlux.buffer(3);

        StepVerifier
                .create(bufferedFlux)
                .expectNext(Arrays.asList("apple", "orange", "banana"))
                .expectNext(Arrays.asList("kiwi", "strawberry"))
                .verifyComplete();
    }

    @Test
    public void bufferAndFlatMap() throws Exception {
        //буфер и flat позволяют вместе разбивать поток на куски и обрабатывать их параллельно
        Flux.just("apple", "orange", "banana", "kiwi", "strawberry")
                .buffer(3)
                .flatMap(x ->
                        Flux.fromIterable(x)
                                .map(y -> y.toUpperCase())
                                .subscribeOn(Schedulers.parallel())
                                .log()
                ).subscribe();

        /**
         * для того чтобы наглядно увидеть параллельность вот логи
         * 00:14:44.138 [main] INFO reactor.Flux.SubscribeOn.1 -- onSubscribe(FluxSubscribeOn.SubscribeOnSubscriber)
         * 00:14:44.143 [main] INFO reactor.Flux.SubscribeOn.1 -- request(32)
         * 00:14:44.146 [main] INFO reactor.Flux.SubscribeOn.2 -- onSubscribe(FluxSubscribeOn.SubscribeOnSubscriber)
         * 00:14:44.146 [main] INFO reactor.Flux.SubscribeOn.2 -- request(32)
         * 00:14:44.147 [parallel-2] INFO reactor.Flux.SubscribeOn.2 -- onNext(KIWI)
         * 00:14:44.147 [parallel-2] INFO reactor.Flux.SubscribeOn.2 -- onNext(STRAWBERRY)
         * 00:14:44.147 [parallel-1] INFO reactor.Flux.SubscribeOn.1 -- onNext(APPLE)
         * 00:14:44.147 [parallel-1] INFO reactor.Flux.SubscribeOn.1 -- onNext(ORANGE)
         * 00:14:44.147 [parallel-1] INFO reactor.Flux.SubscribeOn.1 -- onNext(BANANA)
         * 00:14:44.147 [parallel-1] INFO reactor.Flux.SubscribeOn.1 -- onComplete()
         * 00:14:44.147 [parallel-2] INFO reactor.Flux.SubscribeOn.2 -- onComplete()
         */
    }

    @Test
    public void collectList() {
        Flux<String> fruitFlux = Flux.just(
                "apple", "orange", "banana", "kiwi", "strawberry");

        //просто собирает поток в лист
        //Flux<List<String>> bufferedFlux = fruitFlux.buffer(); альтернативный вариант
        Mono<List<String>> fruitListMono = fruitFlux.collectList();

        StepVerifier
                .create(fruitListMono)
                .expectNext(Arrays.asList(
                        "apple", "orange", "banana", "kiwi", "strawberry"))
                .verifyComplete();
    }

    @Test
    public void collectMap() {
        Flux<String> animalFlux = Flux.just(
                "aardvark", "elephant", "koala", "eagle", "kangaroo");

        //collectMap создает поток Mono с мепой
        Mono<Map<Character, String>> animalMapMono =
                animalFlux.collectMap(a -> a.charAt(0));

        StepVerifier
                .create(animalMapMono)
                .expectNextMatches(map -> {
                    return
                            map.size() == 3 &&
                                    map.get('a').equals("aardvark") &&
                                    map.get('e').equals("eagle") &&
                                    map.get('k').equals("kangaroo");
                })
                .verifyComplete();
    }
}
