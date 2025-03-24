/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "opto/chaitin.hpp"
#include "opto/opcodes.hpp"
#include "opto/regmask.hpp"
#include "unittest.hpp"

// Sanity tests for RegMask and RegMaskIterator. The file tests operations on
// combinations of different RegMask versions ("basic", i.e. only statically
// allocated and "extended", i.e. extended with dynamically allocated memory).

static void contains_expected_num_of_registers(const RegMask& rm, unsigned int expected) {

  ASSERT_TRUE(rm.Size() == expected);
  if (expected > 0) {
    ASSERT_TRUE(rm.is_NotEmpty());
  } else {
    ASSERT_TRUE(!rm.is_NotEmpty());
    ASSERT_TRUE(!rm.is_AllStack());
  }

  RegMaskIterator rmi(rm);
  unsigned int count = 0;
  OptoReg::Name reg = OptoReg::Bad;
  while (rmi.has_next()) {
    reg = rmi.next();
    ASSERT_TRUE(OptoReg::is_valid(reg));
    count++;
  }
  ASSERT_EQ(OptoReg::Bad, rmi.next());
  ASSERT_TRUE(count == expected);
}

TEST_VM(RegMask, empty) {
  RegMask rm;
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, iteration) {
  RegMask rm;
  rm.Insert(30);
  rm.Insert(31);
  rm.Insert(32);
  rm.Insert(33);
  rm.Insert(62);
  rm.Insert(63);
  rm.Insert(64);
  rm.Insert(65);

  RegMaskIterator rmi(rm);
  ASSERT_TRUE(rmi.next() == OptoReg::Name(30));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(31));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(32));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(33));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(62));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(63));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(64));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(65));
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, Set_ALL) {
  // Check that Set_All doesn't add bits outside of rm.rm_size_bits()
  RegMask rm;
  rm.Set_All();
  ASSERT_TRUE(rm.Size() == rm.rm_size_bits());
  ASSERT_TRUE(rm.is_NotEmpty());
  // Set_All sets AllStack bit
  ASSERT_TRUE(rm.is_AllStack());
  contains_expected_num_of_registers(rm, rm.rm_size_bits());
}

TEST_VM(RegMask, Clear) {
  // Check that Clear doesn't leave any stray bits
  RegMask rm;
  rm.Set_All();
  rm.Clear();
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, AND) {
  RegMask rm1;
  rm1.Insert(OptoReg::Name(1));
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(1)));

  rm1.AND(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  rm1.AND(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, OR) {
  RegMask rm1;
  rm1.Insert(OptoReg::Name(1));
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(1)));

  rm1.OR(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  rm1.OR(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, SUBTRACT) {
  RegMask rm1;
  RegMask rm2;

  rm2.Set_All();
  for (int i = 17; i < (int)rm1.rm_size_bits(); i++) {
    rm1.Insert(i);
  }
  rm1.set_AllStack();
  ASSERT_TRUE(rm1.is_AllStack());
  rm2.SUBTRACT(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_bits() - 17);
  contains_expected_num_of_registers(rm2, 17);
}

TEST_VM(RegMask, SUBTRACT_inner) {
  RegMask rm1;
  RegMask rm2;
  rm2.Set_All();
  for (int i = 17; i < (int)rm1.rm_size_bits(); i++) {
    rm1.Insert(i);
  }
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_bits() - 17);
  contains_expected_num_of_registers(rm2, 17);
}

TEST_VM(RegMask, is_bound1) {
  RegMask rm;
  ASSERT_FALSE(rm.is_bound1());
  for (int i = 0; i < (int)rm.rm_size_bits() - 1; i++) {
    rm.Insert(i);
    ASSERT_TRUE(rm.is_bound1())       << "Index " << i;
    ASSERT_TRUE(rm.is_bound(Op_RegI)) << "Index " << i;
    contains_expected_num_of_registers(rm, 1);
    rm.Remove(i);
  }
  // AllStack bit does not count as a bound register
  rm.set_AllStack();
  ASSERT_FALSE(rm.is_bound1());
}

TEST_VM(RegMask, is_bound_pair) {
  RegMask rm;
  ASSERT_TRUE(rm.is_bound_pair());
  for (int i = 0; i < (int)rm.rm_size_bits() - 2; i++) {
    rm.Insert(i);
    rm.Insert(i + 1);
    ASSERT_TRUE(rm.is_bound_pair())   << "Index " << i;
    ASSERT_TRUE(rm.is_bound_set(2))   << "Index " << i;
    ASSERT_TRUE(rm.is_bound(Op_RegI)) << "Index " << i;
    contains_expected_num_of_registers(rm, 2);
    rm.Clear();
  }
  // A pair with the AllStack bit does not count as a bound pair
  rm.Clear();
  rm.Insert(rm.rm_size_bits() - 2);
  rm.Insert(rm.rm_size_bits() - 1);
  rm.set_AllStack();
  ASSERT_FALSE(rm.is_bound_pair());
}

TEST_VM(RegMask, is_bound_set) {
  RegMask rm;
  for (int size = 1; size <= 16; size++) {
    ASSERT_TRUE(rm.is_bound_set(size));
    for (int i = 0; i < (int)rm.rm_size_bits() - size; i++) {
      for (int j = i; j < i + size; j++) {
        rm.Insert(j);
      }
      ASSERT_TRUE(rm.is_bound_set(size))   << "Size " << size << " Index " << i;
      contains_expected_num_of_registers(rm, size);
      rm.Clear();
    }
    // A set with the AllStack bit does not count as a bound set
    for (int j = rm.rm_size_bits() - size; j < (int)rm.rm_size_bits(); j++) {
      rm.Insert(j);
    }
    rm.set_AllStack();
    ASSERT_FALSE(rm.is_bound_set(size));
    rm.Clear();
  }
}

TEST_VM(RegMask, external_member) {
  RegMask rm;
  rm.set_AllStack(false);
  ASSERT_FALSE(rm.Member(OptoReg::Name(rm.rm_size_bits())));
  rm.set_AllStack(true);
  ASSERT_TRUE(rm.Member(OptoReg::Name(rm.rm_size_bits())));
}

TEST_VM(RegMask, find_element) {
  RegMask rm;
  rm.Insert(OptoReg::Name(44));
  rm.Insert(OptoReg::Name(30));
  rm.Insert(OptoReg::Name(54));
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Name(30));
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Name(54));
  rm.set_AllStack(true);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Name(54));
  rm.Clear();
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Bad);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Bad);
}

TEST_VM(RegMask, find_first_set) {
  RegMask rm;
  LRG lrg;
  lrg._is_scalable = 0;
  lrg._is_vector = 0;
  ASSERT_EQ(rm.find_first_set(lrg, 2), OptoReg::Bad);
  rm.Insert(OptoReg::Name(24));
  rm.Insert(OptoReg::Name(25));
  rm.Insert(OptoReg::Name(26));
  rm.Insert(OptoReg::Name(27));
  rm.Insert(OptoReg::Name(16));
  rm.Insert(OptoReg::Name(17));
  rm.Insert(OptoReg::Name(18));
  rm.Insert(OptoReg::Name(19));
  ASSERT_EQ(rm.find_first_set(lrg, 4), OptoReg::Name(19));
}

TEST_VM(RegMask, alignment) {
  RegMask rm;
  rm.Insert(OptoReg::Name(30));
  rm.Insert(OptoReg::Name(31));
  ASSERT_TRUE(rm.is_aligned_sets(2));
  rm.Insert(OptoReg::Name(32));
  rm.Insert(OptoReg::Name(37));
  rm.Insert(OptoReg::Name(62));
  rm.Insert(OptoReg::Name(71));
  rm.Insert(OptoReg::Name(74));
  rm.Insert(OptoReg::Name(75));
  ASSERT_FALSE(rm.is_aligned_pairs());
  rm.clear_to_pairs();
  ASSERT_TRUE(rm.is_aligned_sets(2));
  ASSERT_TRUE(rm.is_aligned_pairs());
  contains_expected_num_of_registers(rm, 4);
  ASSERT_TRUE(rm.Member(OptoReg::Name(30)));
  ASSERT_TRUE(rm.Member(OptoReg::Name(31)));
  ASSERT_TRUE(rm.Member(OptoReg::Name(74)));
  ASSERT_TRUE(rm.Member(OptoReg::Name(75)));
  ASSERT_FALSE(rm.is_misaligned_pair());
  rm.Remove(OptoReg::Name(30));
  rm.Remove(OptoReg::Name(74));
  ASSERT_TRUE(rm.is_misaligned_pair());
}

TEST_VM(RegMask, clear_to_sets) {
  RegMask rm;
  rm.Insert(OptoReg::Name(3));
  rm.Insert(OptoReg::Name(20));
  rm.Insert(OptoReg::Name(21));
  rm.Insert(OptoReg::Name(22));
  rm.Insert(OptoReg::Name(23));
  rm.Insert(OptoReg::Name(25));
  rm.Insert(OptoReg::Name(26));
  rm.Insert(OptoReg::Name(27));
  rm.Insert(OptoReg::Name(40));
  rm.Insert(OptoReg::Name(42));
  rm.Insert(OptoReg::Name(43));
  rm.Insert(OptoReg::Name(44));
  rm.Insert(OptoReg::Name(45));
  rm.clear_to_sets(2);
  ASSERT_TRUE(rm.is_aligned_sets(2));
  contains_expected_num_of_registers(rm, 10);
  rm.clear_to_sets(4);
  ASSERT_TRUE(rm.is_aligned_sets(4));
  contains_expected_num_of_registers(rm, 4);
  rm.clear_to_sets(8);
  ASSERT_TRUE(rm.is_aligned_sets(8));
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, smear_to_sets) {
  RegMask rm;
  rm.Insert(OptoReg::Name(3));
  rm.smear_to_sets(2);
  ASSERT_TRUE(rm.is_aligned_sets(2));
  contains_expected_num_of_registers(rm, 2);
  rm.smear_to_sets(4);
  ASSERT_TRUE(rm.is_aligned_sets(4));
  contains_expected_num_of_registers(rm, 4);
  rm.smear_to_sets(8);
  ASSERT_TRUE(rm.is_aligned_sets(8));
  contains_expected_num_of_registers(rm, 8);
  rm.smear_to_sets(16);
  ASSERT_TRUE(rm.is_aligned_sets(16));
  contains_expected_num_of_registers(rm, 16);
}

TEST_VM(RegMask, overlap) {
  RegMask rm1;
  RegMask rm2;
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.Insert(OptoReg::Name(23));
  rm1.Insert(OptoReg::Name(2));
  rm1.Insert(OptoReg::Name(12));
  rm2.Insert(OptoReg::Name(1));
  rm2.Insert(OptoReg::Name(4));
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.Insert(OptoReg::Name(4));
  ASSERT_TRUE(rm1.overlap(rm2));
  ASSERT_TRUE(rm2.overlap(rm1));
}

TEST_VM(RegMask, valid_reg) {
  RegMask rm;
  ASSERT_FALSE(rm.is_valid_reg(OptoReg::Name(42), 1));
  rm.Insert(OptoReg::Name(3));
  rm.Insert(OptoReg::Name(5));
  rm.Insert(OptoReg::Name(6));
  rm.Insert(OptoReg::Name(7));
  ASSERT_FALSE(rm.is_valid_reg(OptoReg::Name(7), 4));
  ASSERT_TRUE(rm.is_valid_reg(OptoReg::Name(7), 2));
}

TEST_VM(RegMask, rollover_and_insert_remove) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_bits() + 42);
  OptoReg::Name reg2(rm.rm_size_bits() * 2 + 42);
  rm.set_AllStack(true);
  ASSERT_TRUE(rm.Member(reg1));
  rm.rollover();
  rm.Clear();
  rm.Insert(reg1);
  ASSERT_TRUE(rm.Member(reg1));
  rm.Remove(reg1);
  ASSERT_FALSE(rm.Member(reg1));
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  rm.Insert(reg2);
  ASSERT_FALSE(rm.Member(reg1));
  ASSERT_TRUE(rm.Member(reg2));
}

TEST_VM(RegMask, rollover_and_find) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_bits() + 42);
  OptoReg::Name reg2(rm.rm_size_bits() + 7);
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Bad);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Bad);
  rm.Insert(reg1);
  rm.Insert(reg2);
  ASSERT_EQ(rm.find_first_elem(), reg2);
  ASSERT_EQ(rm.find_last_elem(), reg1);
}

TEST_VM(RegMask, rollover_and_find_first_set) {
  LRG lrg;
  lrg._is_scalable = 0;
  lrg._is_vector = 0;
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_bits() + 24);
  OptoReg::Name reg2(rm.rm_size_bits() + 25);
  OptoReg::Name reg3(rm.rm_size_bits() + 26);
  OptoReg::Name reg4(rm.rm_size_bits() + 27);
  OptoReg::Name reg5(rm.rm_size_bits() + 16);
  OptoReg::Name reg6(rm.rm_size_bits() + 17);
  OptoReg::Name reg7(rm.rm_size_bits() + 18);
  OptoReg::Name reg8(rm.rm_size_bits() + 19);
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  ASSERT_EQ(rm.find_first_set(lrg, 2), OptoReg::Bad);
  rm.Insert(reg1);
  rm.Insert(reg2);
  rm.Insert(reg3);
  rm.Insert(reg4);
  rm.Insert(reg5);
  rm.Insert(reg6);
  rm.Insert(reg7);
  rm.Insert(reg8);
  ASSERT_EQ(rm.find_first_set(lrg, 4), reg8);
}

TEST_VM(RegMask, rollover_and_Set_All_From) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_bits() + 42);
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  rm.Set_All_From(reg1);
  contains_expected_num_of_registers(rm, rm.rm_size_bits() - 42);
}

TEST_VM(RegMask, rollover_and_Set_All_From_Offset) {
  RegMask rm;
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  rm.Set_All_From_Offset();
  contains_expected_num_of_registers(rm, rm.rm_size_bits());
}

TEST_VM(RegMask, rollover_and_iterate) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_bits() + 2);
  OptoReg::Name reg2(rm.rm_size_bits() + 6);
  OptoReg::Name reg3(rm.rm_size_bits() + 17);
  OptoReg::Name reg4(rm.rm_size_bits() + 43);
  rm.set_AllStack(true);
  rm.rollover();
  rm.Clear();
  rm.Insert(reg1);
  rm.Insert(reg2);
  rm.Insert(reg3);
  rm.Insert(reg4);
  RegMaskIterator rmi(rm);
  ASSERT_EQ(rmi.next(), reg1);
  ASSERT_EQ(rmi.next(), reg2);
  ASSERT_EQ(rmi.next(), reg3);
  ASSERT_EQ(rmi.next(), reg4);
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, rollover_and_SUBTRACT_inner_disjoint) {
  RegMask rm1;
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_bits() + 42);
  rm1.set_AllStack(true);
  rm1.rollover();
  rm1.Clear();
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.Insert(reg1);
  rm2.Insert(42);
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 1);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 1);
}

TEST_VM(RegMask, rollover_and_SUBTRACT_inner_overlap) {
  RegMask rm1;
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_bits() + 42);
  rm1.set_AllStack(true);
  rm1.rollover();
  rm1.Clear();
  rm2.set_AllStack(true);
  rm2.rollover();
  rm2.Clear();
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.Insert(reg1);
  rm2.Insert(reg1);
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm1.Insert(reg1);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
}

#ifndef PRODUCT

Arena* arena() {
  return Thread::current()->resource_area();
}

static void is_basic(const RegMask& rm) {
  ASSERT_EQ(rm.rm_size(), RegMask::basic_rm_size());
}

static void is_extended(const RegMask& rm) {
  ASSERT_TRUE(rm.rm_size() > RegMask::basic_rm_size());
}

static int first_extended() {
  return RegMask::basic_rm_size() * BitsPerWord;
}

static void extend(RegMask& rm, unsigned int n = 4) {
  // Extend the given RegMask with at least n dynamically-allocated words.
  rm.Insert(OptoReg::Name(first_extended() + (BitsPerWord * n) - 1));
  rm.Clear();
  ASSERT_TRUE(rm.rm_size() >= RegMask::basic_rm_size() + n);
}

TEST_VM(RegMask, static_by_default) {
  // Check that a freshly created RegMask does not allocate dynamic memory.
  RegMask rm;
  is_basic(rm);
}

TEST_VM(RegMask, iteration_extended) {
  RegMask rm(arena());
  rm.Insert(30);
  rm.Insert(31);
  rm.Insert(33);
  rm.Insert(62);
  rm.Insert(first_extended());
  rm.Insert(first_extended() + 42);
  rm.Insert(first_extended() + 55);
  rm.Insert(first_extended() + 456);

  RegMaskIterator rmi(rm);
  ASSERT_TRUE(rmi.next() == OptoReg::Name(30));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(31));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(33));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(62));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended()));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 42));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 55));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 456));
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, Set_ALL_extended) {
  // Check that Set_All doesn't add bits outside of rm.rm_size_bits() on
  // extended RegMasks.
  RegMask rm(arena());
  extend(rm);
  rm.Set_All();
  ASSERT_EQ(rm.Size(), rm.rm_size_bits());
  ASSERT_TRUE(rm.is_NotEmpty());
  // Set_All sets AllStack bit
  ASSERT_TRUE(rm.is_AllStack());
  contains_expected_num_of_registers(rm, rm.rm_size_bits());
}

TEST_VM(RegMask, Set_ALL_From_extended) {
  RegMask rm(arena());
  extend(rm);
  rm.Set_All_From(OptoReg::Name(42));
  contains_expected_num_of_registers(rm, rm.rm_size_bits() - 42);
}

TEST_VM(RegMask, Set_ALL_From_extended_grow) {
  RegMask rm(arena());
  rm.Set_All_From(first_extended() + OptoReg::Name(42));
  is_extended(rm);
  contains_expected_num_of_registers(rm, rm.rm_size_bits() - first_extended() - 42);
}

TEST_VM(RegMask, Clear_extended) {
  // Check that Clear doesn't leave any stray bits on extended RegMasks.
  RegMask rm(arena());
  rm.Insert(first_extended());
  is_extended(rm);
  rm.Set_All();
  rm.Clear();
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, AND_extended_basic) {
  RegMask rm1(arena());
  rm1.Insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(first_extended())));

  rm1.AND(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  is_basic(rm2);
  rm1.AND(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, AND_extended_extended) {
  RegMask rm1(arena());
  rm1.Insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(first_extended())));

  rm1.AND(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2(arena());
  extend(rm2);
  rm1.AND(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, OR_extended_basic) {
  RegMask rm1(arena());
  rm1.Insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(first_extended())));

  rm1.OR(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  is_basic(rm2);
  rm1.OR(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, OR_extended_extended) {
  RegMask rm1(arena());
  rm1.Insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(first_extended())));

  rm1.OR(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2(arena());
  extend(rm2);
  rm1.OR(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, SUBTRACT_extended) {
  RegMask rm1(arena());
  extend(rm1);
  RegMask rm2(arena());
  extend(rm2);

  rm2.Set_All();
  ASSERT_TRUE(rm2.is_AllStack());
  for (int i = first_extended() + 17; i < (int)rm1.rm_size_bits(); i++) {
    rm1.Insert(i);
  }
  rm1.set_AllStack(true);
  ASSERT_TRUE(rm1.is_AllStack());
  rm2.SUBTRACT(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_bits() - first_extended() - 17);
  contains_expected_num_of_registers(rm2, first_extended() + 17);
}

TEST_VM(RegMask, external_member_extended) {
  RegMask rm(arena());
  extend(rm);
  rm.set_AllStack(false);
  ASSERT_FALSE(rm.Member(OptoReg::Name(rm.rm_size_bits())));
  rm.set_AllStack(true);
  ASSERT_TRUE(rm.Member(OptoReg::Name(rm.rm_size_bits())));
}

TEST_VM(RegMask, overlap_extended) {
  RegMask rm1(arena());
  extend(rm1);
  RegMask rm2(arena());
  extend(rm2);
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.Insert(OptoReg::Name(23));
  rm1.Insert(OptoReg::Name(2));
  rm1.Insert(OptoReg::Name(first_extended() + 12));
  rm2.Insert(OptoReg::Name(1));
  rm2.Insert(OptoReg::Name(first_extended() + 4));
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.Insert(OptoReg::Name(first_extended() + 4));
  ASSERT_TRUE(rm1.overlap(rm2));
  ASSERT_TRUE(rm2.overlap(rm1));
}

TEST_VM(RegMask, up_extended) {
  RegMask rm(arena());
  extend(rm);
  ASSERT_TRUE(rm.is_UP());
  rm.Insert(OptoReg::Name(1));
  ASSERT_TRUE(rm.is_UP());
  rm.Insert(OptoReg::Name(first_extended()));
  ASSERT_FALSE(rm.is_UP());
  rm.Clear();
  rm.set_AllStack(true);
  ASSERT_FALSE(rm.is_UP());
}

TEST_VM(RegMask, SUBTRACT_inner_basic_extended) {
  RegMask rm1;
  RegMask rm2(arena());
  rm1.Insert(OptoReg::Name(1));
  rm1.Insert(OptoReg::Name(42));
  is_basic(rm1);
  rm2.Insert(OptoReg::Name(1));
  rm2.Insert(OptoReg::Name(first_extended() + 20));
  is_extended(rm2);
  rm1.SUBTRACT_inner(rm2);
  is_basic(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(42)));
}

TEST_VM(RegMask, SUBTRACT_inner_extended_basic) {
  RegMask rm1(arena());
  RegMask rm2;
  rm1.Insert(OptoReg::Name(1));
  rm1.Insert(OptoReg::Name(42));
  rm1.Insert(OptoReg::Name(first_extended() + 20));
  is_extended(rm1);
  rm2.Insert(OptoReg::Name(1));
  is_basic(rm2);
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 2);
  ASSERT_TRUE(rm1.Member(OptoReg::Name(42)));
  ASSERT_TRUE(rm1.Member(OptoReg::Name(first_extended() + 20)));
}

TEST_VM(RegMask, rollover_extended) {
  RegMask rm(arena());
  extend(rm);
  is_extended(rm);
  OptoReg::Name reg1(rm.rm_size_bits() + 42);
  rm.set_AllStack(true);
  rm.rollover();
  rm.Insert(reg1);
  ASSERT_TRUE(rm.Member(reg1));
}

TEST_VM(RegMask, rollover_and_SUBTRACT_inner_disjoint_extended) {
  RegMask rm1(arena());
  RegMask rm2;
  extend(rm1);
  OptoReg::Name reg1(rm1.rm_size_bits() + 42);
  rm1.set_AllStack(true);
  rm1.rollover();
  rm1.Clear();
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.Insert(reg1);
  rm2.Insert(42);
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 1);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 1);
}

TEST_VM(RegMask, rollover_and_SUBTRACT_inner_overlap_extended) {
  RegMask rm1(arena());
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_bits() + 42);
  extend(rm1);
  rm2.set_AllStack(true);
  rm2.rollover();
  rm2.Clear();
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.Insert(reg1);
  rm2.Insert(reg1);
  rm1.SUBTRACT_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm1.Insert(reg1);
  rm2.SUBTRACT_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
}

#endif // !PRODUCT
