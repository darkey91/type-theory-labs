GENERATED = Parser.hs Lexer.hs

.PHONY: all run pack clean

all: parser

run: parser
	./parser

parser: $(GENERATED) Main.hs Synt.hs
	ghc -i./ -tmpdir . ./Main.hs -o parser

$(GENERATED): Parser.y Lexer.x Main.hs Synt.hs
	alex Lexer.x -o Lexer.hs
	happy Parser.y -o Parser.hs

pack: $(GENERATED)
	zip taskA.zip *

clean:
	rm -f *.hi *.o  
	rm -f $(GENERATED)





