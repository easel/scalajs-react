package japgolly.scalajs.react

import ComponentScope._

object CompState {

  // ===================================================================================================================
  // Accessor
  // ===================================================================================================================

  sealed abstract class Accessor[$$, S] {
    final type $ = $$
    def state   ($: $): S
    def setState($: $)(s: S, cb: Callback): Unit
    def modState($: $)(f: S => S, cb: Callback): Unit
    def zoom[T](f: S => T)(g: (S, T) => S): Accessor[$, T]
  }
  object RootAccessor {
    private[this] val instance = new RootAccessor[Any]
    def apply[S] = instance.asInstanceOf[RootAccessor[S]]
  }
  class RootAccessor[S] extends Accessor[CanSetState[S], S] {
    override def state   ($: $)                          = $._state.v
    override def setState($: $)(s: S, cb: Callback)      = $._setState(WrapObj(s), cb.toJsCallback)
    override def modState($: $)(f: S => S, cb: Callback) = $._setState(WrapObj(f(state($))), cb.toJsCallback)
    def zoom[T](f: S => T)(g: (S, T) => S): Accessor[$, T] =
      new ZoomAccessor[S, T](this, f, g)
  }
  class ZoomAccessor[S, T](parent: RootAccessor[S], get: S => T, set: (S, T) => S) extends Accessor[CanSetState[S], T] {
    override def state   ($: $)                          = get(parent state $)
    override def setState($: $)(t: T, cb: Callback)      = parent.modState($)(s => set(s, t), cb)
    override def modState($: $)(f: T => T, cb: Callback) = parent.modState($)(s => set(s, f(get(s))), cb)
    def zoom[U](f: T => U)(g: (T, U) => T): Accessor[$, U] =
      new ZoomAccessor[S, U](parent, f compose get, (s, u) => set(s, g(get(s), u)))
  }

  // ===================================================================================================================
  // Ops traits
  // ===================================================================================================================

  trait BaseOps[S] {
    type This[_]
    protected type $$
    protected val $: $$
    protected val a: Accessor[$$, S]
    protected def changeAccessor[T](a2: Accessor[$$, T]): This[T]

    def accessCB: StateAccessCB[S]
    def accessDirect: StateAccessDirect[S]

    final def zoom[T](f: S => T)(g: (S, T) => S): This[T] =
      changeAccessor(a.zoom(f)(g))
  }

  trait ReadDirectOps[S] extends BaseOps[S] {
    type This[T] <: ReadDirectOps[T]
    final def state: S = a state $
  }
  trait ReadCallbackOps[S] extends BaseOps[S] {
    type This[T] <: ReadCallbackOps[T]
    final def state: CallbackTo[S] = CallbackTo(a state $)
  }

  trait WriteOps[S] extends BaseOps[S] {
    type This[T] <: WriteOps[T]
    type WriteResult
    def setState  (s: S            , cb: Callback = Callback.empty): WriteResult
    def modState  (f: S => S       , cb: Callback = Callback.empty): WriteResult
    def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): WriteResult

    final def modStateCB(f: S => CallbackTo[S], cb: Callback = Callback.empty): WriteResult =
      modState(f andThen (_.runNow()), cb)

    final def _setState[I](f: I => S, cb: Callback = Callback.empty): I => WriteResult =
      i => setState(f(i), cb)

    final def _modState[I](f: I => S => S, cb: Callback = Callback.empty): I => WriteResult =
      i => modState(f(i), cb)

    final def _setStateCB[I](f: I => CallbackTo[S], cb: Callback = Callback.empty): I => WriteResult =
      i => setStateCB(f(i), cb)

    final def _modStateCB[I](f: I => S => CallbackTo[S], cb: Callback = Callback.empty): I => WriteResult =
      i => modStateCB(f(i), cb)
  }

  type WriteOpAux[S, Result] = WriteOps[S] { type WriteResult = Result }

  trait WriteDirectOps[S] extends WriteOps[S] {
    type This[T] <: WriteDirectOps[T]
    final type WriteResult = Unit
    final override def setState  (s: S            , cb: Callback = Callback.empty): Unit = a.setState($)(s, cb)
    final override def modState  (f: S => S       , cb: Callback = Callback.empty): Unit = a.modState($)(f, cb)
    final override def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): Unit = setState(s.runNow(), cb)
  }
  trait WriteCallbackOps[S] extends WriteOps[S] {
    type This[T] <: WriteCallbackOps[T]
    final type WriteResult = Callback
    final override def setState  (s: S            , cb: Callback = Callback.empty): Callback = CallbackTo(a.setState($)(s, cb))
    final override def modState  (f: S => S       , cb: Callback = Callback.empty): Callback = CallbackTo(a.modState($)(f, cb))
    final override def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): Callback = s >>= (setState(_, cb))
  }

  trait ReadDirectWriteDirectOps[S] extends ReadDirectOps[S] with WriteDirectOps[S] {
    override final type This[T] = ReadDirectWriteDirectOps[T]
  }
  trait ReadDirectWriteCallbackOps[S] extends ReadDirectOps[S] with WriteCallbackOps[S] {
    override final type This[T] = ReadDirectWriteCallbackOps[T]
  }
  trait ReadCallbackWriteCallbackOps[S] extends ReadCallbackOps[S] with WriteCallbackOps[S] {
    override final type This[T] = ReadCallbackWriteCallbackOps[T]
  }

  // ===================================================================================================================
  // Ops impls
  // ===================================================================================================================

  private[react] final class ReadDirectWriteDirect[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadDirectWriteDirectOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadDirectWriteDirect($, a2)
    override def accessCB: ReadCallbackWriteCallbackOps[S] = new ReadCallbackWriteCallback($, a)
    override def accessDirect: ReadDirectWriteDirectOps[S] = this
  }

  private[react] final class ReadDirectWriteCallback[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadDirectWriteCallbackOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadDirectWriteCallback($, a2)
    override def accessCB: ReadCallbackWriteCallbackOps[S] = new ReadCallbackWriteCallback($, a)
    override def accessDirect: ReadDirectWriteDirectOps[S] = new ReadDirectWriteDirect($, a)
  }

  private[react] final class ReadCallbackWriteCallback[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadCallbackWriteCallbackOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadCallbackWriteCallback($, a2)
    override def accessCB: ReadCallbackWriteCallbackOps[S] = this
    override def accessDirect: ReadDirectWriteDirectOps[S] = new ReadDirectWriteDirect($, a)
  }
}
