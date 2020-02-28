{
module Parser where

import Lexer
import Synt
}

%name parser
%tokentype { Token }
%error { parseError }

%token
	'.'	{ TokenDot }
	'\\'	{ TokenLambda }
	'('	{ TokenLeftParenthesis }
	')'	{ TokenRightParenthesis }
	VAR	{ TokenVar $$ }	
%%

Expr
	: Appl	{ $1 }
	| '\\' Var '.' Expr	{ Atom $2 $4 }
	| Appl '\\' Var '.' Expr { Appl $1 (Atom $3 $5) }

Atom 
	: '(' Expr ')' { $2 }
	| Var	{ $1 }

Appl	
	: Appl Atom	{ Appl $1 $2 }
	| Atom	{ $1 }

Var
	: VAR { Var $1 }

{

parseError :: [Token] -> a
parseError e = error "Parse error"

}

