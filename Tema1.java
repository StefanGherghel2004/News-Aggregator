import java.io.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Tema1 {

	private static ExecutorService tpe;

	public static final BlockingQueue<Article> allArticles = new LinkedBlockingQueue<>();

	// set concurent ordonat ce stocheaza articolele
	public static final ConcurrentSkipListSet<Article> parsedUniqueArticles = new ConcurrentSkipListSet<>();

	// structuri concurente pentru determinarea duplicatelor
	public static final ConcurrentHashMap<String, AtomicInteger> uuidCount = new ConcurrentHashMap<>();
	public static final ConcurrentHashMap<String, AtomicInteger> titleCount = new ConcurrentHashMap<>();

	// structuri concurente pentru statisticile priving autorii/limba/categoriile (in grupurile de limba/ categorii stocheaz
	// doar uuid)
	public static final ConcurrentHashMap<String, AtomicInteger> authorCount = new ConcurrentHashMap<>();
	public static final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> langGroup = new ConcurrentHashMap<>();
	public static final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> categGroup = new ConcurrentHashMap<>();

	// set-uri folosite pentru generarea statisticilor (populate de cate un singur thread)
	public static final Set<String> languages = new HashSet<>();
	public static final Set<String> categories = new HashSet<>();
	public static final Set<String> linkingWords = new HashSet<>();

	// variabile volatile pentru propagarea imediata a modificarii valorilor
	public static volatile String topKeywordEN = "";
	public static volatile int topKeywordENCount = 0;

	public static volatile String topAuthor = "";
	public static volatile int topAuthorCnt = 0;

	public static volatile String topLang = "";
	public static volatile int topLangCnt = 0;

	public static volatile String topCateg = "";
	public static volatile int topCategCnt = 0;

	// folosit pentru actualizarea concurenta a duplicatelor
	public static final AtomicLong duplicates = new AtomicLong(0);

	// coada ce stocheaza cai catre fisierele cu articole
	public static final BlockingQueue<Path> fileQueue = new LinkedBlockingQueue<>();

	// structura concurenta globala pentru contorizarea frecventei cuvintelor din articole
	public static final ConcurrentHashMap<String, AtomicInteger> wordFreq = new ConcurrentHashMap<>();

	public static void main(String[] args) {

		int threads = Integer.parseInt(args[0]);
		String fisierArticole = args[1];
		String fisierSuplimentar = args[2];
		Path langPath = null;
		Path categoryPath = null;
		Path wordsPath = null;

		try (BufferedReader br = new BufferedReader(new FileReader(fisierArticole))) {
			int n = Integer.parseInt(br.readLine().trim());
			for (int i = 0; i < n; i++) {
				String path = br.readLine().trim();
				// replace general pentru deteminarea caii spre fisier relativ la locatia executabilului
				path = path.replace("../../articles/", "../checker/input/articles/");

				Path p = Paths.get(path);

				// adaugarea cailor catre fisierele cu articole in coada de procesare
				fileQueue.add(p);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (BufferedReader br = new BufferedReader(new FileReader(fisierSuplimentar))) {
			int n = Integer.parseInt(br.readLine().trim());
			for (int i = 0; i < n; i++) {
				String path = br.readLine().trim();
				// replace general pentru deteminarea caii spre fisier relativ la locatia executabilului
				path = path.replace("../../files/", "../checker/input/files/");

				// necesar pentru testul test_small
				if (path.startsWith("./"))
					path = path.replace("./", "../checker/input/tests/test_small/");

				// extragerea cailor pentru fisierele aditionale
				if (i == 0)
					langPath = Paths.get(path);
				if (i == 1)
					categoryPath = Paths.get(path);
				if (i == 2)
					wordsPath = Paths.get(path);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// stocam informatiile despre limbi, categorii si cuvinte de legatura in structuri corespunzatoare
		getInfoFromPath(langPath, languages, false);
		getInfoFromPath(categoryPath, categories, true);
		getInfoFromPath(wordsPath, linkingWords, false);

		// latch ce marcheaza prima etapa de parsare finalizata cand  = 0 (identificarea duplicatelor)
		CountDownLatch latch = new CountDownLatch(threads);
		// latch ce marcheaza a doua etapa de parsare finalizata cand = 0 (captarea informatiilor generale pentru statistici)
		CountDownLatch latch1 = new CountDownLatch(threads);
		// latch ce marcheaza finalizarea computarilor de top/maxim si printarea keyword-urilor cand = 0
		CountDownLatch latch2 = new CountDownLatch(4);

		// pool cu numthreads thread-uri ce vor lua task-uri din pool
		tpe = Executors.newFixedThreadPool(threads);

		// workerii care actualizeaza contoarele pentru determinarea duplicatelor si stocheaza toate articolele
		for (int i = 0; i < threads; i++)
			tpe.submit(new MyThread(fileQueue, latch));

		// workerii care proceseaza initial articoelel unicate si le stocheaza in structura ordonata
		for (int i = 0; i < threads; i++)
			tpe.submit(new ProcessArticles(latch, latch1));

		// scrierea tuturor articolelor unicate
		tpe.submit(() -> printAllArticles(latch1));

		//  printarea fisierelor pe categorii si limbi
		tpe.submit(() -> printGroupStats(latch1, languages, langGroup));
		tpe.submit(() -> printGroupStats(latch1, categories, categGroup));

		// determinarea statisticilor de tip top/max
		tpe.submit(() -> computeTopAuthor(latch1, latch2)); // *
		tpe.submit(() -> computeTopLang(latch1, latch2));   // *
		tpe.submit(() -> computeTopCateg(latch1, latch2)); 	// *

		// printarea fisierului cu keywords (si determinarea top keyWord)
		tpe.submit(() -> printKeyWordsCnt(latch1, latch2)); // *

		// printarea statisticlor finale (asteapta ca cele 4 task-uri comentate cu steluta sa se termine)
		tpe.submit(() -> printReports(latch2));

		// inchiderea thread-urilor
		tpe.shutdown();


	}

	private static void printGroupStats(CountDownLatch awaitLatch, Set<String> possibleEntries, ConcurrentHashMap<String, ConcurrentSkipListSet<String>> entries) {
		try {
			awaitLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		for (String entry : possibleEntries) {

			if (!entries.containsKey(entry)) {
				continue;
			}
			try (PrintWriter pw = new PrintWriter(entry + ".txt")) {
				for (var groupEntry : entries.get(entry)) {
					pw.println(groupEntry);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private static void getInfoFromPath(Path path, Set<String> set, boolean normalize) {
		try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
			int rows = Integer.parseInt(br.readLine().trim());
			for (int i = 0; i < rows; i++) {
				if (!normalize) {
					set.add(br.readLine().trim());
				} else {
					set.add(normalizeCategory(br.readLine().trim()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public static void addParsedArticle(Article a) {
		parsedUniqueArticles.add(a);
	}

	public static String normalizeCategory(String category) {
		return category.replace(",", "").trim().replaceAll("\\s+", "_");
	}

	public static List<String> parseWords(String text) {
		List<String> result = new ArrayList<>();

		if (text == null || text.isEmpty())
			return result;

		for (String raw : text.toLowerCase().split("\\s+")) {
			StringBuilder sb = new StringBuilder();

			for (char c : raw.toCharArray()) {
				if (c >= 'a' && c <= 'z') {
					sb.append(c);
				}
			}

			if (!sb.isEmpty()) {
				result.add(sb.toString());
			}
		}

		return result;
	}


	public static void increaseDuplicates() {
		duplicates.incrementAndGet();
	}

	private static void printAllArticles(CountDownLatch waitLatch) {
		try (PrintWriter pw = new PrintWriter("all_articles.txt")) {
			waitLatch.await();
			for (Article a : parsedUniqueArticles) {
				pw.println(a.uuid + " " + a.published);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void computeTopAuthor(CountDownLatch waitLatch, CountDownLatch downlatch) {

		try {
			waitLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			downlatch.countDown();
		}

		for (var e : authorCount.entrySet()) {
			String author = e.getKey();
			int count = e.getValue().get();

			if (count > topAuthorCnt || (count == topAuthorCnt && author.compareTo(topAuthor) < 0)) {
				topAuthor = author;
				topAuthorCnt = count;
			}
		}

		downlatch.countDown();
	}

	private static void computeTopLang(CountDownLatch waitLatch,CountDownLatch downLatch) {

		try {
			waitLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			downLatch.countDown();
		}

		for (var e : langGroup.entrySet()) {
			String lang = e.getKey();
			int count = e.getValue().size();

			if (count > topLangCnt || (count == topLangCnt && lang.compareTo(topLang) < 0)) {
				topLang = lang;
				topLangCnt = count;
			}
		}

		downLatch.countDown();
	}

	private static void computeTopCateg(CountDownLatch waitLatch, CountDownLatch downLatch) {

		try {
			waitLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			downLatch.countDown();
		}

		for (var entry : categGroup.entrySet()) {
			String categ = entry.getKey();
			int count = entry.getValue().size();

			if (count > topCategCnt ||
					(count == topCategCnt && categ.compareTo(topCateg) < 0)) {

				topCateg = categ;
				topCategCnt = count;
			}
		}

		downLatch.countDown();
	}

	private static void printReports(CountDownLatch waitLatch) {

		try {
			waitLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		try (PrintWriter pw = new PrintWriter("reports.txt")) {
			pw.println("duplicates_found - " + duplicates);
			pw.println("unique_articles - " + parsedUniqueArticles.size());
			pw.println("best_author - " + topAuthor + " " + topAuthorCnt);
			pw.println("top_language - " + topLang + " " + topLangCnt);
			pw.println("top_category - " + topCateg + " " + topCategCnt);
			pw.println("most_recent_article - " + parsedUniqueArticles.first().published + " " + parsedUniqueArticles.first().url);
			pw.println("top_keyword_en - " + topKeywordEN + " " + topKeywordENCount);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void printKeyWordsCnt(CountDownLatch waitLatch, CountDownLatch downLatch) {
		try (PrintWriter pw = new PrintWriter("keywords_count.txt")) {

			waitLatch.await();
			// wordFreq este deja calculat de ProcessArticles
			var top = Tema1.wordFreq.entrySet().stream()
					.max((e1, e2) -> {
						int cmp = Integer.compare(e1.getValue().get(), e2.getValue().get());
						if (cmp != 0)
							return cmp;
						return e2.getKey().compareTo(e1.getKey());
					})
					.orElse(null);

			// determinarea topKeyWord-ului
			if (top != null) {
				topKeywordEN = top.getKey();
				topKeywordENCount = top.getValue().get();
			}

			Tema1.wordFreq.entrySet().stream()
					.sorted((e1, e2) -> {
						int cmp = Integer.compare(e2.getValue().get(), e1.getValue().get());
						if (cmp != 0)
							return cmp;
						return e1.getKey().compareTo(e2.getKey());
					})
					.forEach(e -> pw.println(e.getKey() + " " + e.getValue().get()));

			downLatch.countDown();

		} catch (Exception e) {
			e.printStackTrace();
			downLatch.countDown();
		}
	}



}