import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class MyThread implements Runnable {

	private final BlockingQueue<Path> fileQueue;
	private final CountDownLatch downLatch;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public MyThread(BlockingQueue<Path> fileQueue, CountDownLatch downLatch) {
		this.fileQueue = fileQueue;
		this.downLatch = downLatch;
	}

	@Override
	public void run() {
		try {
			while (true) {
				Path file = fileQueue.poll();

				if (file == null) {
					downLatch.countDown();
					return;
				}

				String json = Files.readString(file);
				Article[] articles = OBJECT_MAPPER.readValue(json, Article[].class);

				for (Article a : articles) {

					// actualizam contoarele pentru UUID si Title
					int uuid = Tema1.uuidCount.computeIfAbsent(a.uuid, x -> new AtomicInteger()).incrementAndGet();
					int title = Tema1.titleCount.computeIfAbsent(a.title, x -> new AtomicInteger()).incrementAndGet();

					// salvam articolul  pentru etapa 2 (procesare articole unice)
					Tema1.allArticles.add(a);

				}
			}
		} catch (Exception e) {
			downLatch.countDown();
		}
	}
}