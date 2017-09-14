package com.billding

/**
  * Created by bfrasure on 7/1/17.
  */
class PropertyCheckSandbox {

}

sealed abstract class Tree
case class Node(left: Tree, right: Tree, v: Int) extends Tree
case object Leaf extends Tree

import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

object PropertyCheckSandbox {
  val genLeaf = const(Leaf)

  val genNode = for {
    v <- arbitrary[Int]
    left <- genTree
    right <- genTree
  } yield Node(left, right, v)

  def genTree: Gen[Tree] = oneOf(genLeaf, genNode)
}
