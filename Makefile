JAVAC = javac
JAVA = java
SRCDIR = .
SOURCES = $(wildcard $(SRCDIR)/*.java $(SRCDIR)/*/*.java)
CLASSES = $(SOURCES:.java=.class)
MAIN_CLASS = Tema1

.PHONY: build clean run

build: $(CLASSES)

%.class: %.java
	$(JAVAC) -cp $(SRCDIR) $<

run:
	$(JAVA) -cp $(SRCDIR) $(MAIN_CLASS) $(ARGS)

clean:
	rm -f $(SRCDIR)/*.class $(SRCDIR)/*/*.class
	rm -f $(SRCDIR)/*.txt


