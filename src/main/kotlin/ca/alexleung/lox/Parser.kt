package ca.alexleung.lox

class Parser(private val tokens: List<Token>) {
    private var current: Int = 0;

    class ParseError : RuntimeException()

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            val declarationStatement = declaration() ?: continue
            statements.add(declarationStatement)
        }
        return statements
    }

    private fun declaration(): Stmt? {
        try {
            if (match(TokenType.VAR)) {
                return varDeclaration()
            }
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")

        var initializer: Expr? = null

        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Var(name, initializer)
    }

    private fun statement(): Stmt {
        if (match(TokenType.IF)) {
            return ifStatement()
        }

        if (match(TokenType.PRINT)) {
            return printStatement()
        }

        if (match(TokenType.LEFT_BRACE)) {
            return Block(block())
        }

        return expressionStatement()
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()
        val elseBranch = if (match(TokenType.ELSE)) statement() else null

        return If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Print(expr)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression.")
        return Expression(expr)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            val declarationStatement = declaration() ?: continue
            statements.add(declarationStatement)
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = equality()
        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Variable) {
                return Assign(expr.name, value)
            }

            error(equals, "Invalid assignment target.")
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = term()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous()
            val right = factor()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()

        while (match(TokenType.STAR, TokenType.SLASH)) {
            val operator = previous()
            val right = unary()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }

        return primary()
    }

    private fun primary(): Expr {
        if (match(TokenType.TRUE)) {
            return Literal(true)
        }

        if (match(TokenType.FALSE)) {
            return Literal(false)
        }

        if (match(TokenType.NIL)) {
            return Literal(null)
        }

        if (match(TokenType.NUMBER, TokenType.STRING)) {
            val literal = previous().literal
            return Literal(literal)
        }

        if (match(TokenType.IDENTIFIER)) {
            return Variable(previous())
        }

        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression")
            return Grouping(expr)
        }

        throw error(peek(), "Expecting an expression.")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) {
            return advance()
        }

        throw error(peek(), message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) {
            return false
        }
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) {
            current++
        }
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type == TokenType.EOF
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun error(token: Token, message: String): ParseError {
        Lox.error(token, message)
        return ParseError()
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) {
                return
            }

            when (peek().type) {
                TokenType.CLASS,
                TokenType.FUN,
                TokenType.VAR,
                TokenType.FOR,
                TokenType.IF,
                TokenType.WHILE,
                TokenType.PRINT,
                TokenType.RETURN -> return
                else -> {
                    // No-op
                }
            }

            advance()
        }
    }
}
