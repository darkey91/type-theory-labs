module Synt where

data Expr 
	= Atom Expr Expr
	| Appl Expr Expr
	| Var String

instance Show Expr where
	show (Atom expr1 expr2)  = "(\\" ++ show expr1 ++ "." ++ show expr2 ++ ")" 
	show (Appl expr1 expr2) = "(" ++ show expr1 ++ " " ++ show expr2 ++ ")"
	show (Var name) = name 



