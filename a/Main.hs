module Main where

import Parser(parser)
import Lexer

main :: IO()
main = do
        input <- getContents
        putStrLn . show . parser . alexScanTokens $ input
