package scalaz.stream

import Process._

/** 
 * Module of various `Tee` processes.
 */
trait tee {
  
  /** A `Tee` which alternates between emitting values from the left input and the right input. */
  def interleave[I]: Tee[I,I,I] = repeat { for {
    i1 <- awaitL[I]
    i2 <- awaitR[I]
    r <- emit(i1) ++ emit(i2)
  } yield r }

  /** A `Tee` which ignores all input from left. */
  def passR[I2]: Tee[Any,I2,I2] = awaitR[I2].repeat
  
  /* A `Tee` which ignores all input from the right. */
  def passL[I]: Tee[I,Any,I] = awaitL[I].repeat

  /** Echoes the right branch until the left branch becomes `true`, then halts. */
  def until[I]: Tee[Boolean,I,I] = 
    awaitL[Boolean].flatMap(kill => if (kill) halt else awaitR[I] fby until)

  /** Echoes the right branch when the left branch is `true`. */ 
  def when[I]: Tee[Boolean,I,I] = 
    awaitL[Boolean].flatMap(ok => if (ok) awaitR[I] fby when else when)
    
  /** Defined as `zipWith((_,_))` */
  def zip[I,I2]: Tee[I,I2,(I,I2)] = zipWith((_,_))

  /** A version of `zip` that pads the shorter stream with values. */
  def zipAll[I,I2](padI: I, padI2: I2): Tee[I,I2,(I,I2)] = 
    zipWithAll(padI, padI2)((_,_))
  
  /** 
   * Zip together two inputs, then apply the given function,
   * halting as soon as either input is exhausted. 
   * This implementation reads from the left, then the right. 
   */
  def zipWith[I,I2,O](f: (I,I2) => O): Tee[I,I2,O] = { for {
    i <- awaitL[I]
    i2 <- awaitR[I2]
    r <- emit(f(i,i2))
  } yield r } repeat


  /** A version of `zipWith` that pads the shorter stream with values. */
  def zipWithAll[I,I2,O](padI: I, padI2: I2)(
                         f: (I,I2) => O): Tee[I,I2,O] = {
    val fbR: Tee[I,I2,O] = passR[I2] map (f(padI, _    ))
    val fbL: Tee[I,I2,O] = passL[I]  map (f(_   , padI2))
    receiveLOr(fbR)(i => 
    receiveROr(tee.feed1L(i)(fbL))(i2 => emit(f(i,i2)))) repeat
  }
}

object tee extends tee {

  /** Feed one input to the left branch of this `Tee`. */
  def feed1L[I,I2,O](i: I)(t: Tee[I,I2,O]): Tee[I,I2,O] =
    wye.feed1L(i)(t).asInstanceOf[Tee[I,I2,O]]

  /** Feed one input to the right branch of this `Tee`. */
  def feed1R[I,I2,O](i2: I2)(t: Tee[I,I2,O]): Tee[I,I2,O] =
    wye.feed1R(i2)(t).asInstanceOf[Tee[I,I2,O]]
}
