/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_REGALLOC_HPP
#define SHARE_OPTO_REGALLOC_HPP

#include "code/vmreg.hpp"
#include "opto/block.hpp"
#include "opto/matcher.hpp"
#include "opto/phase.hpp"

class Node;
class Matcher;
class PhaseCFG;

#define  MAX_REG_ALLOCATORS   10

//------------------------------PhaseRegAlloc------------------------------------
// Abstract register allocator
class PhaseRegAlloc : public Phase {
  friend class VMStructs;
  static void (*_alloc_statistics[MAX_REG_ALLOCATORS])();
  static int _num_allocators;

protected:
  uint                        _post_alloc_node_limit;
  GrowableArray<OptoRegPair>* _node_regs;
  VectorSet                   _node_oops;         // Mapping from node indices to oopiness

  void alloc_node_regs(int size);  // allocate _node_regs table with at least "size" elements

  PhaseRegAlloc( uint unique, PhaseCFG &cfg, Matcher &matcher,
                 void (*pr_stats)());
public:
  PhaseCFG &_cfg;               // Control flow graph
  uint _framesize;              // Size of frame in stack-slots. not counting preserve area
  OptoReg::Name _max_reg;       // Past largest register seen
  Matcher &_matcher;            // Convert Ideal to MachNodes
  uint post_alloc_node_limit() const { return _post_alloc_node_limit; }
  uint initial;
  uint original;
  uint max;
  uint max_expand_limit;

  // Get the first/second registers associated with the Node, if explicitly
  // assigned, or OptoReg::Bad otherwise.
  OptoReg::Name get_reg_first(const Node* n) const {
    if (n->_idx >= (uint)_node_regs->length()) {
      return OptoReg::Bad;
    }
    return _node_regs->at(n->_idx).first();
  }
  OptoReg::Name get_reg_second( const Node *n ) const {
    if (n->_idx >= (uint)_node_regs->length()) {
      return OptoReg::Bad;
    }
    return _node_regs->at(n->_idx).second();
  }

  // Do all the real work of allocate
  virtual void Register_Allocate() = 0;


  // notify the register allocator that "node" is a new reference
  // to the value produced by "old_node"
  virtual void add_reference( const Node *node, const Node *old_node) = 0;

  // Set the register associated with a new Node
  void set1_no_grow(uint idx, OptoReg::Name reg) {
    _node_regs->at(idx).set1(reg);
  }
  void set2_no_grow(uint idx, OptoReg::Name reg) {
    _node_regs->at(idx).set2(reg);
  }
  void set_pair_no_grow(uint idx, OptoReg::Name hi, OptoReg::Name lo) {
    _node_regs->at_put(idx, OptoRegPair(hi, lo));
  }
  void set_bad(uint idx) {
    _node_regs->at_put_grow(idx, OptoRegPair());
    if ((uint)idx + 1 > max) {
      max = idx + 1;
    }
  }
  void set_pair(uint idx, OptoReg::Name hi, OptoReg::Name lo) {
    _node_regs->at_put_grow(idx, OptoRegPair(hi, lo));
    if ((uint)idx + 1 > max) {
      max = idx + 1;
    }
  }

  // Set and query if a node produces an oop
  void set_oop( const Node *n, bool );
  bool is_oop( const Node *n ) const;

  // Convert a register number to a stack offset
  int reg2offset          ( OptoReg::Name reg ) const;
  int reg2offset_unchecked( OptoReg::Name reg ) const;

  // Convert a stack offset to a register number
  OptoReg::Name offset2reg( int stk_offset ) const;

  // Get the register encoding associated with the Node
  int get_encode(const Node *n) const {
    OptoReg::Name first  = get_reg_first(n);
    OptoReg::Name second = get_reg_second(n);
    assert( !OptoReg::is_valid(second) || second == first+1, "" );
    assert(OptoReg::is_reg(first), "out of range");
    return Matcher::_regEncode[first];
  }

#ifndef PRODUCT
  static int _total_framesize;
  static int _max_framesize;

  bool is_node_reg_info_available() const {
    return _node_regs != nullptr;
  }
  virtual void dump_frame() const = 0;
  virtual char *dump_register( const Node *n, char *buf, size_t buf_size) const = 0;
  static void print_statistics();
#endif
};

#endif // SHARE_OPTO_REGALLOC_HPP
