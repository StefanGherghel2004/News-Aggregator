JARS = utils/*

MAIN = Tema1

build:
	javac -cp .:$(JARS) $(MAIN).java MyThread.java Article.java ProcessArticles.java

run:
	java -cp .:$(JARS) $(MAIN) $(ARGS)

clean:
	rm -f *.class *.txt