all: Parser.kt
	kotlinc Parser.kt -include-runtime -d a.jar

run: a.jar
	java -jar a.jar