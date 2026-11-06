package s4y.guess.guesser.domain.vo;

import s4y.guess.guesser.domain.errors.InvalidBit;

/** One bit, 0 or 1. */
public enum Bit {
  ZERO(0),
  ONE(1);

  private final int value;

  Bit(int value) {
    this.value = value;
  }

  /** Makes a bit from 0 or 1. Any other number is an error. */
  public static Bit of(int v) throws InvalidBit {
    return switch (v) {
      case 0 -> ZERO;
      case 1 -> ONE;
      default -> throw new InvalidBit(v);
    };
  }

  public int value() {
    return value;
  }

  public Bit opposite() {
    return this == ZERO ? ONE : ZERO;
  }
}
