package de.kaufhof.ets.logging

trait LogAttributeProcessor[E, O] {
  def process(attributes: Seq[generic.LogAttribute[E]]): O
}
