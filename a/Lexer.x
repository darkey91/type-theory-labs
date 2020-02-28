{
module Lexer where
}

%wrapper "basic"

$digit = 0-9
$letters = [a-z]

tokens :-
	$white+	 ;
	\\	{ \s -> TokenLambda }
	\.	{ \s -> TokenDot }
	\(	{ \s -> TokenLeftParenthesis }
	\)	{ \s -> TokenRightParenthesis }
	$letters [$letters $digit \']*	{ \s -> TokenVar s}
	

{ 

data Token = 
	TokenLambda
	| TokenLeftParenthesis
	| TokenRightParenthesis
	| TokenVar String
	| TokenDot
	deriving (Eq, Show)
	
} 

