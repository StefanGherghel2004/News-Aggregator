import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessArticles implements Runnable {

	private final CountDownLatch waitLatch;
	private final CountDownLatch downLatch;

	public ProcessArticles(CountDownLatch waitLatch, CountDownLatch downLatch) {
		this.waitLatch = waitLatch;
		this.downLatch = downLatch;
	}

	@Override
	public void run() {
		try {
			// asteptam ca mythread sa termine incarcarea tuturor articolelor
			waitLatch.await();

			while (true) {

				Article a = Tema1.allArticles.poll();

				// daca coada este goala => am terminat
				if (a == null) {
					downLatch.countDown();
					return;
				}

				int uuidCnt = Tema1.uuidCount.get(a.uuid).get();
				int titleCnt = Tema1.titleCount.get(a.title).get();

				// procesam doar articolele care nu sunt duplicate
				if (uuidCnt == 1 && titleCnt == 1) {

					// stocam articolul in structura ordonata
					Tema1.addParsedArticle(a);

					// actualizam counter-ul pentru autor, categorie si limba
					Tema1.authorCount.computeIfAbsent(a.author, x -> new AtomicInteger()).incrementAndGet();

					for (String cat : a.categories) {
						String norm = Tema1.normalizeCategory(cat);
						Tema1.categGroup.computeIfAbsent(norm, k -> new ConcurrentSkipListSet<>()).add(a.uuid);
					}

					Tema1.langGroup.computeIfAbsent(a.language, k -> new ConcurrentSkipListSet<>()).add(a.uuid);

					// pentru articolele in limba engleza facem procesarea cuvintelor din text
					if (Objects.equals(a.language, "english")) {

						List<String> words = Tema1.parseWords(a.text);
						HashSet<String> used = new HashSet<>();

						for (String w : words) {
							// nu luam in calcul cuvintele de legatura
							if (!w.isEmpty() && !Tema1.linkingWords.contains(w)) {
								used.add(w);
							}
						}

						// actualizam in structura globala concurenta
						for (String word : used) {
							Tema1.wordFreq.computeIfAbsent(word, x -> new AtomicInteger()).incrementAndGet();
						}
					}

				} else {
					// actualizarea numarului de duplicate in counter-ul global concurent
					Tema1.increaseDuplicates();
				}
			}

		} catch (Exception ignored) {
			downLatch.countDown();
		}
	}
}