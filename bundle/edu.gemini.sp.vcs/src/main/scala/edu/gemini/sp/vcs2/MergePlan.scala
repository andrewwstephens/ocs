package edu.gemini.sp.vcs2

import edu.gemini.pot.sp._
import edu.gemini.pot.sp.version._
import edu.gemini.shared.util._
import edu.gemini.sp.vcs2.NodeDetail.Obs
import edu.gemini.sp.vcs2.VcsFailure._
import edu.gemini.spModel.rich.pot.sp._

import scalaz._
import Scalaz._

/** Describes the modifications required for a local program to complete a
  * merge.
  */
case class MergePlan(update: Tree[MergeNode], delete: Set[Missing]) {


  /** True if the MergePlan contains no updates. */
  def isEmpty: Boolean = update.rootLabel match {
    case Unmodified(_) => delete.isEmpty
    case _             => false
  }

  def nonEmpty: Boolean = !isEmpty

  /** "Encode" for serialization. The issue is that `scalaz.Tree` is not
    * `Serializable` but we need to send `MergePlan`s over `trpc`. */
  def encode: MergePlan.Transport = {
    def encodeTree(t: Tree[MergeNode]): MergePlan.TreeTransport =
      MergePlan.TreeTransport(t.rootLabel, t.subForest.toList.map(encodeTree))

    MergePlan.Transport(encodeTree(update), delete)
  }

  /** Gets the `VersionMap` of the provided program as it will be after the
    * updates in this plan have been applied. */
  def vm(p: ISPProgram): VersionMap = {
    // Extract the updates to the VersionMap from the MergePlan.
    val vmUpdates: VersionMap = {
      val vm0 = update.foldRight(Map.empty[SPNodeKey, NodeVersions]) { (mn, m) =>
        mn match {
          case Modified(k, nv, _, _, _) => m.updated(k, nv)
          case _                        => m
        }
      }
      (vm0/:delete) { case (vm1, Missing(k, nv)) => vm1.updated(k, nv) }
    }

    p.getVersions ++ vmUpdates
  }

  /** Compares the version information in this merge plan with the given
    * version map.  The assumption here is that any unmodified parts of the
    * program tree have the same version information and do not need to be
    * considered in the calculation. */
  def compare(vm: VersionMap): VersionComparison = {
    val vm0 = vm.withDefaultValue(EmptyNodeVersions)
    val up  = update.foldMap {
      case Modified(k, nv, _, _, _) => VersionComparison.compare(nv, vm0(k))
      case Unmodified(_)            => VersionComparison.Same
    }
    val del = delete.foldMap {
      case Missing(k, nv) => VersionComparison.compare(nv, vm0(k))
    }
    up |+| del
  }

  /** True if this plan contains non-empty `Conflicts`, false otherwise.
   */
  def hasConflicts: Boolean =
    update.sFoldRight(false) { (mn, b) =>
      b || (mn match {
        case Modified(_, _, _, _, con) => !con.isEmpty
        case _                         => false
      })
    }

  /** Accepts a program and edits it according to this merge plan. */
  def merge(f: ISPFactory, p: ISPProgram): VcsAction[Unit] = {
    // Tries to create an ISPNode from the information in the MergeNode.
    def create(mn: MergeNode): TryVcs[ISPNode] =
      mn match {
        case Modified(k, _, dob, _, _) =>
          NodeFactory.mkNode(f, p, dob.getType, Some(k)) \/>
            Unexpected("Could not create science program node of type: " + dob.getType)
        case Unmodified(k)             =>
          Unexpected(s"Unmodified node with key $k not found in program ${p.getProgramID}.").left
      }

    // Edit the ISPNode, applying the changes in the MergeNode if any.
    def edit(t: Tree[(MergeNode, ISPNode)]): Unit = {
      t.rootLabel match {

        case (Modified(_, nv, dob, det, con), n) =>
          n.setDataObject(dob)
          n.setConflicts(con)

          // If it is an observation, set the observation number.
          (det, n) match {
            case (Obs(num), o: ISPObservation) => o.setObservationNumber(num)
            case _                             => // not an observation
          }

          // Edit then set the children.
          t.subForest.foreach(edit)
          n.children = t.subForest.toList.map(_.rootLabel._2)

        case (Unmodified(_), _) => // do nothing
      }
    }

    // Pair up MergeNodes with their corresponding ISPNode, creating any missing
    // ISPNodes as necessary.
    val mergeTree: VcsAction[Tree[(MergeNode, ISPNode)]] = {
      val nodeMap = p.nodeMap

      update.traverseU { mn =>
        nodeMap.get(mn.key).fold(create(mn)) { _.right }.strengthL(mn)
      }
    }.liftVcs

    def doEdit(mt: Tree[(MergeNode, ISPNode)]): VcsAction[Unit] =
      \/.fromTryCatch {
        edit(mt)
        p.setVersions(vm(p))
      }.leftMap(VcsException).liftVcs

    mergeTree >>= doEdit
  }
}

object MergePlan {

  /** A serializable Tree[MergeNode].  Sadly scalaz.Tree is not serializable. */
  case class TreeTransport(mn: MergeNode, children: List[TreeTransport]) {
    def decode: Tree[MergeNode] = Tree.node(mn, children.map(_.decode).toStream)
  }

  /** A serializable MergePlan.  Sadly the Tree[MergeNode] contained in the
    * MergePlan is not serializable.
    */
  case class Transport(update: TreeTransport, delete: Set[Missing]) {
    def decode: MergePlan = MergePlan(update.decode, delete)
  }
}
