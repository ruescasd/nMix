package org.nvotes.libmix

import ch.bfh.unicrypt.math.algebra.general.interfaces.Element
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModSafePrime

/** The group and generator for an election
 *
 */
case class CryptoSettings(group: GStarModSafePrime, generator: Element[_])

/**
 * Serialization (Data Transfer Object) classes
 *
 * This allows us to simulate sending messages over the wire as well
 * as posting to bulletin boards in a neutral format (pure strings/json)
 */
case class EncryptionKeyShareDTO(sigmaProofDTO: SigmaProofDTO, keyShare: String)

case class PartialDecryptionDTO(partialDecryptions: Seq[String], proofDTO: SigmaProofDTO)
case class SigmaProofDTO(commitment: String, challenge: String, response: String)

case class ShuffleResultDTO(shuffleProof: ShuffleProofDTO, votes: Seq[String])
case class PermutationProofDTO(commitment: String, challenge: String, response: String,
  bridgingCommitments: Seq[String], eValues: Seq[String])
case class MixProofDTO(commitment: String, challenge: String, response: String, eValues: Seq[String])
case class ShuffleProofDTO(mixProof: MixProofDTO, permutationProof: PermutationProofDTO, permutationCommitment: String)