import java

newtype TMaybeExpr =
  TExpr(Expr c) or
  TNoExpr()

class MaybeExpr extends TMaybeExpr {
  abstract string toString();
  abstract Location getLocation();
}

class YesMaybeExpr extends MaybeExpr {
  Expr c;

  YesMaybeExpr() { this = TExpr(c) }
  override string toString() { result = c.toString() }
  override Location getLocation() { result = c.getLocation() }
}

class NoMaybeExpr extends MaybeExpr {
  NoMaybeExpr() { this = TNoExpr() }

  override string toString() { result = "<none>" }
  override Location getLocation() { none() }
}

MaybeExpr initializer(Variable v) {
  if exists(v.getInitializer())
  then result = TExpr(v.getInitializer())
  else result = TNoExpr()
}

from Variable v
select v, v.getType(), initializer(v)

