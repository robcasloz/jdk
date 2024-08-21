/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_REGMASK_HPP
#define SHARE_OPTO_REGMASK_HPP

#include "code/vmreg.hpp"
#include "opto/optoreg.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/globalDefinitions.hpp"
#include "memory/arena.hpp"

class LRG;

//-------------Non-zero bit search methods used by RegMask---------------------
// Find lowest 1, undefined if empty/0
static unsigned int find_lowest_bit(uintptr_t mask) {
  return count_trailing_zeros(mask);
}
// Find highest 1, undefined if empty/0
static unsigned int find_highest_bit(uintptr_t mask) {
  return count_leading_zeros(mask) ^ (BitsPerWord - 1U);
}

//------------------------------RegMask----------------------------------------
// The ADL file describes how to print the machine-specific registers, as well
// as any notion of register classes.  We provide a register mask, which is
// just a collection of Register numbers.

// The ADLC defines 2 macros, RM_SIZE and FORALL_BODY.
// RM_SIZE is the base size of a register mask in 32-bit words.
// FORALL_BODY replicates a BODY macro once per word in the register mask.
// The usage is somewhat clumsy and limited to the regmask.[h,c]pp files.
// However, it means the ADLC can redefine the unroll macro and all loops
// over register masks will be unrolled by the correct amount.

class RegMask {

  friend class RegMaskIterator;

  // The RM_SIZE is aligned to 64-bit - assert that this holds
  LP64_ONLY(STATIC_ASSERT(is_aligned(RM_SIZE, 2)));

  static const unsigned int _WordBitMask = BitsPerWord - 1U;
  static const unsigned int _LogWordBits = LogBitsPerWord;
  static const unsigned int _RM_SIZE     = LP64_ONLY(RM_SIZE >> 1) NOT_LP64(RM_SIZE);
  static const unsigned int _RM_MAX      = _RM_SIZE - 1U;

  union {
    // Array of Register Mask bits.  This array is large enough to cover all
    // the machine registers and all parameters that need to be passed on the
    // stack (stack registers) up to some interesting limit. On Intel, the
    // limit is something like 90+ parameters.
    int       _RM_I[RM_SIZE];
    uintptr_t _RM_UP[_RM_SIZE];
  };

  // In rare situations (e.g., "more than 90+ parameters on Intel"), we need to
  // extend the register mask with dynamically allocated memory. We could use a
  // GrowableArray here, but there are currently some GrowableArray limitations
  // that have a negative performance impact for our use case:
  //
  // - There is no efficient copy/clone operation.
  // - GrowableArray construction currently default-initializes everything
  //   within their capacity, which is unnecessary in our case.
  //
  // After addressing these limitations, we should consider using a
  // GrowableArray here.
  uintptr_t* _RM_UP_EXT = nullptr;

#ifdef ASSERT
  // Register masks may get shallowly copied without the use of constructors,
  // which is problematic when dealing with the externally allocated memory for
  // _RM_UP_EXT. Therefore, we need some sanity checks to ensure we have not
  // missed any such cases. The below variables enable such checks.
  //
  // The original address of the _RM_UP_EXT variable, set when using
  // constructors. If we get copied/cloned, &_RM_UP_EXT will no longer equal
  // orig_ext_adr.
  uintptr_t** orig_ext_adr = &_RM_UP_EXT;
  //
  // If the original version is read-only. In such cases, we can allow
  // read-only sharing.
  public: bool orig_const = false;
  private:
#endif

  // Current total register mask size in words
  unsigned int _rm_size;

  // We support offsetting register masks to present different views of the
  // register space, mainly for use in PhaseChaitin::Select. The _offset
  // variable indicates how many words we offset with. We consider all
  // registers before the offset to not be included in the register mask.
  unsigned int _offset;

  // If _all_stack = true, we consider all registers beyond what the register
  // mask can currently represent to be included. If _all_stack = false, we
  // consider the registers not included.
  bool _all_stack = false;

  // The low and high watermarks represent the lowest and highest word
  // that might contain set register mask bits, respectively. We guarantee
  // that there are no bits in words outside this range, but any word at
  // and between the two marks can still be 0.
  unsigned int _lwm;
  unsigned int _hwm;

  // The following diagram illustrates the internal representation of a RegMask
  // (with _offset = 0, for a made-up platform with 10 registers and 4-bit
  // words) that has been extended with two additional words to represent more
  // stack locations:
  //                                   _hwm=3
  //            _lwm=1                RM_SIZE=3                _rm_size=5
  //              |                       |                        |
  //   r0 r1 r2 r3 r4 r5 r6 r7 r8 r9 s0 s1   s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 ...
  //  [0  0  0  0 |0  1  1  0 |0  0  1  0 ] [1  1  0  1 |0  0  0  0] as  as  as
  // [0]         [1]         [2]           [0]         [1]
  //
  // \____________________________________/ \______________________/
  //                        |                           |
  //                      RM_UP                     RM_UP_EXT
  // \_____________________________________________________________/
  //                                 |
  //                             _rm_size
  //
  // In this example, registers {r5, r6} and stack locations {s0, s2, s3, s5}
  // are included in the register mask. Depending on the value of _all_stack,
  // (s10, s11, ...) are all included (as = 1) or excluded (as = 0). Note that
  // all registers/stack locations under _lwm and over _hwm are excluded.
  // The exception is (s10, s11, ...), where the value is decided solely by
  // _all_stack, regardless of the value of _hwm.

  // Access word i in the register mask.
  const uintptr_t& _rm_up(unsigned int i) const {
    assert(orig_const || orig_ext_adr == &_RM_UP_EXT, "clone sanity check");
    if (i < _RM_SIZE) {
      return _RM_UP[i];
    } else {
      assert(_RM_UP_EXT != nullptr, "sanity");
      return _RM_UP_EXT[i - _RM_SIZE];
    }
  }

  // Non-const version of the above.
  uintptr_t& _rm_up(unsigned int i) {
    assert(orig_ext_adr == &_RM_UP_EXT, "clone sanity check");
    return const_cast<uintptr_t&>(const_cast<const RegMask*>(this)->_rm_up(i));
  }

  // The maximum word index
  unsigned int _rm_max() const { return _rm_size - 1U; }

  // Return a suitable arena for (extended) register mask allocation.
  static Arena* _get_arena();

  // Grow the register mask to ensure it can fit at least min_size words.
  void _grow(unsigned int min_size, bool init = true) {
    if (min_size > _rm_size) {
      Arena* _arena = _get_arena();
      min_size = round_up_power_of_2(min_size);
      unsigned int old_size = _rm_size;
      unsigned int old_ext_size = old_size - _RM_SIZE;
      unsigned int new_ext_size = min_size - _RM_SIZE;
      _rm_size = min_size;
      if (_RM_UP_EXT == nullptr) {
        assert(old_ext_size == 0, "sanity");
        _RM_UP_EXT = NEW_ARENA_ARRAY(_arena, uintptr_t, new_ext_size);
      } else {
        assert(orig_ext_adr == &_RM_UP_EXT, "clone sanity check");
        _RM_UP_EXT = REALLOC_ARENA_ARRAY(_arena, uintptr_t, _RM_UP_EXT,
                                         old_ext_size, new_ext_size);
      }
      if (init) {
        int fill = 0;
        if (is_AllStack()) {
          fill = 0xFF;
          _hwm = _rm_max();
        }
        _set_range(old_size, fill, _rm_size - old_size);
      }
    }
  }

  // Make us a copy of src
  void _copy(const RegMask& src) {
    assert(_offset == src._offset, "offset mismatch");
    _hwm = src._hwm;
    _lwm = src._lwm;

    // Copy base mask
    memcpy(_RM_UP, src._RM_UP, sizeof(uintptr_t) * _RM_SIZE);
    _all_stack = src._all_stack;

    // Copy extension
    if (src._RM_UP_EXT != nullptr) {
      assert(src._rm_size > _RM_SIZE, "sanity");
      _grow(src._rm_size, false);
      assert(orig_ext_adr == &_RM_UP_EXT, "clone sanity check");
      memcpy(_RM_UP_EXT, src._RM_UP_EXT,
          sizeof(uintptr_t) * (src._rm_size - _RM_SIZE));
    }

    // If the source is smaller than us, we need to set the gap according to
    // the sources all_stack flag.
    if (src._rm_size < _rm_size) {
      int value = 0;
      if (src.is_AllStack()) {
        value = 0xFF;
        _hwm = _rm_max();
      }
      _set_range(src._rm_size, value, _rm_size - src._rm_size);
    }

    assert(valid_watermarks(), "post-condition");
  }

  // Set a span of words in the register mask to a given value.
  void _set_range(unsigned int start, int value, unsigned int length) {
    if (start < _RM_SIZE) {
      memset(_RM_UP + start, value,
             sizeof(uintptr_t) * MIN2((int)length,(int)_RM_SIZE-(int)start));
    }
    if (start + length > _RM_SIZE) {
      assert(_RM_UP_EXT != nullptr, "sanity");
      assert(orig_ext_adr == &_RM_UP_EXT, "clone sanity check");
      memset(_RM_UP_EXT + MAX2((int)start-(int)_RM_SIZE,0), value,
             sizeof(uintptr_t) * MIN2((int)length,
                                      (int)length-((int)_RM_SIZE-(int)start)));
    }
  }

 public:

  unsigned int rm_size() const { return _rm_size; }
  unsigned int rm_size_bits() const { return _rm_size * BitsPerWord; }

  bool is_offset() const { return _offset > 0; }
  unsigned int offset() const { return _offset; }
  unsigned int offset_bits() const { return _offset * BitsPerWord; };

  bool is_AllStack() const { return _all_stack; }
  void set_AllStack(bool value = true) { _all_stack = value; }

  // SlotsPerLong is 2, since slots are 32 bits and longs are 64 bits.
  // Also, consider the maximum alignment size for a normally allocated
  // value.  Since we allocate register pairs but not register quads (at
  // present), this alignment is SlotsPerLong (== 2).  A normally
  // aligned allocated register is either a single register, or a pair
  // of adjacent registers, the lower-numbered being even.
  // See also is_aligned_Pairs() below, and the padding added before
  // Matcher::_new_SP to keep allocated pairs aligned properly.
  // If we ever go to quad-word allocations, SlotsPerQuad will become
  // the controlling alignment constraint.  Note that this alignment
  // requirement is internal to the allocator, and independent of any
  // particular platform.
  enum { SlotsPerLong = 2,
         SlotsPerVecA = 4,
         SlotsPerVecS = 1,
         SlotsPerVecD = 2,
         SlotsPerVecX = 4,
         SlotsPerVecY = 8,
         SlotsPerVecZ = 16,
         SlotsPerRegVectMask = X86_ONLY(2) NOT_X86(1)
         };

  // A constructor only used by the ADLC output.  All mask fields are filled
  // in directly.  Calls to this look something like RM(1,2,3,4);
  RegMask(
#   define BODY(I) int a##I,
    FORALL_BODY
#   undef BODY
    bool all_stack): _rm_size(_RM_SIZE), _offset(0), _all_stack(all_stack) {
#if defined(VM_LITTLE_ENDIAN) || !defined(_LP64)
#   define BODY(I) _RM_I[I] = a##I;
#else
    // We need to swap ints.
#   define BODY(I) _RM_I[I ^ 1] = a##I;
#endif
    FORALL_BODY
#   undef BODY
    _lwm = 0;
    _hwm = _RM_MAX;
    while (_hwm > 0      && _RM_UP[_hwm] == 0) _hwm--;
    while ((_lwm < _hwm) && _RM_UP[_lwm] == 0) _lwm++;
    assert(valid_watermarks(), "post-condition");
  }

  // Construct an empty mask
  RegMask(): _RM_UP(), _rm_size(_RM_SIZE), _offset(0),
             _all_stack(false), _lwm(_RM_MAX), _hwm(0) {
    assert(valid_watermarks(), "post-condition");
  }

  // Construct a mask with a single bit
  RegMask(OptoReg::Name reg DEBUG_ONLY(COMMA bool orig_const = false)): RegMask() {
    Insert(reg);
    DEBUG_ONLY(this->orig_const = orig_const;)
  }

  RegMask(const RegMask& rm): _rm_size(_RM_SIZE), _offset(rm._offset) {
    _copy(rm);
  }

  RegMask& operator= (const RegMask& rm) {
    _copy(rm);
    return *this;
  }

  // Check for register being in mask.
  bool Member(OptoReg::Name reg, bool include_all_stack = false) const {
    reg = reg - offset_bits();
    if (reg < 0) { return false; }
    if (reg >= (int)rm_size_bits()) {
      return include_all_stack ? is_AllStack() : false;
    }
    unsigned int r = (unsigned int)reg;
    return _rm_up(r >> _LogWordBits) & (uintptr_t(1) << (r & _WordBitMask));
  }

  bool Member_including_AllStack(OptoReg::Name reg) const {
    return Member(reg, true);
  }

  // Test for being a not-empty mask. Ignores registers included through the
  // all-stack flag.
  bool is_NotEmpty() const {
    assert(valid_watermarks(), "sanity");
    uintptr_t tmp = 0;
    for (unsigned i = _lwm; i <= _hwm; i++) {
      tmp |= _rm_up(i);
    }
    return tmp;
  }

  // Find lowest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_first_elem() const {
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      uintptr_t bits = _rm_up(i);
      if (bits) {
        return OptoReg::Name(offset_bits() + (i << _LogWordBits) + find_lowest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Get highest-numbered register from mask, or BAD if mask is empty. Ignores
  // registers included through the all-stack flag.
  OptoReg::Name find_last_elem() const {
    assert(valid_watermarks(), "sanity");
    // Careful not to overflow if _lwm == 0
    unsigned i = _hwm + 1;
    while (i > _lwm) {
      uintptr_t bits = _rm_up(--i);
      if (bits) {
        return OptoReg::Name(offset_bits() + (i << _LogWordBits) + find_highest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Clear out partial bits; leave only aligned adjacent bit pairs.
  void clear_to_pairs();

#ifdef ASSERT
  // Verify watermarks are sane, i.e., within bounds and that no
  // register words below or above the watermarks have bits set.
  bool valid_watermarks() const {
    assert(_hwm < _rm_size, "_hwm out of range: %d", _hwm);
    assert(_lwm < _rm_size, "_lwm out of range: %d", _lwm);
    for (unsigned i = 0; i < _lwm; i++) {
      assert(_rm_up(i) == 0, "_lwm too high: %d regs at: %d", _lwm, i);
    }
    for (unsigned i = _hwm + 1; i < _rm_size; i++) {
      assert(_rm_up(i) == 0, "_hwm too low: %d regs at: %d", _hwm, i);
    }
    return true;
  }

  bool is_AllStack_only() const {
    assert(valid_watermarks(), "sanity");
    uintptr_t tmp = 0;
    for (unsigned int i = _lwm; i <= _hwm; i++) {
      tmp |= _rm_up(i);
    }
    return !tmp && is_AllStack();
  }

  bool can_represent(OptoReg::Name reg, unsigned int size = 1) const {
    reg = reg - offset_bits();
    return (int)reg <= (int)(rm_size_bits() - size) && (int)reg >= 0;
  }
#endif // !ASSERT

  // Test that the mask contains only aligned adjacent bit pairs
  bool is_aligned_pairs() const;

  // mask is a pair of misaligned registers
  bool is_misaligned_pair() const;
  // Test for single register
  bool is_bound1() const;
  // Test for a single adjacent pair
  bool is_bound_pair() const;
  // Test for a single adjacent set of ideal register's size.
  bool is_bound(uint ireg) const;

  // Check that whether given reg number with size is valid
  // for current regmask, where reg is the highest number.
  bool is_valid_reg(OptoReg::Name reg, const int size) const;

  // Find the lowest-numbered register set in the mask.  Return the
  // HIGHEST register number in the set, or BAD if no sets.
  // Assert that the mask contains only bit sets.
  OptoReg::Name find_first_set(LRG &lrg, const int size) const;

  // Clear out partial bits; leave only aligned adjacent bit sets of size.
  void clear_to_sets(const unsigned int size);
  // Smear out partial bits to aligned adjacent bit sets.
  void smear_to_sets(const unsigned int size);
  // Test that the mask contains only aligned adjacent bit sets
  bool is_aligned_sets(const unsigned int size) const;

  // Test for a single adjacent set
  bool is_bound_set(const unsigned int size) const;

  static bool is_vector(uint ireg);
  static int num_registers(uint ireg);
  static int num_registers(uint ireg, LRG &lrg);

  // Fast overlap test. Non-zero if any registers in common. Ignores registers
  // included through the all-stack flag.
  bool overlap(const RegMask &rm) const {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    unsigned hwm = MIN2(_hwm, rm._hwm);
    unsigned lwm = MAX2(_lwm, rm._lwm);
    for (unsigned i = lwm; i <= hwm; i++) {
      if (_rm_up(i) & rm._rm_up(i)) {
        return true;
      }
    }
    return false;
  }

  // Special test for register pressure based splitting
  // UP means register only, Register plus stack, or stack only is DOWN
  bool is_UP() const;

  // Clear a register mask. Does not clear any offset.
  void Clear() {
    _lwm = _rm_max();
    _hwm = 0;
    _set_range(0, 0, _rm_size);
    set_AllStack(false);
    assert(valid_watermarks(), "sanity");
  }

  // Fill a register mask with 1's
  void Set_All() {
    assert(_offset == 0, "offset non-zero");
    Set_All_From_Offset();
  }

  // Fill a register mask with 1's from the current offset.
  void Set_All_From_Offset() {
    _lwm = 0;
    _hwm = _rm_max();
    _set_range(0, 0xFF, _rm_size);
    set_AllStack(true);
    assert(valid_watermarks(), "sanity");
  }

  // Fill a register mask with 1's starting from the given register.
  void Set_All_From(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg != OptoReg::Bad, "sanity");
    assert(reg != OptoReg::Special, "sanity");
    assert(reg >= 0, "register outside mask");
    assert(valid_watermarks(), "pre-condition");
    unsigned int r = (unsigned int)reg;
    unsigned int index = r >> _LogWordBits;
    unsigned int min_size = index + 1;
    _grow(min_size);
    _rm_up(index) |= (uintptr_t(-1) << (r & _WordBitMask));
    if (index < _rm_max()) {
      _set_range(index + 1, 0xFF, _rm_max() - index);
    }
    if (index < _lwm) _lwm = index;
    _hwm = _rm_max();
    set_AllStack();
    assert(valid_watermarks(), "post-condition");
  }

  // Insert register into mask
  void Insert(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg != OptoReg::Bad, "sanity");
    assert(reg != OptoReg::Special, "sanity");
    assert(reg >= 0, "register outside mask");
    assert(valid_watermarks(), "pre-condition");
    unsigned int r = (unsigned int)reg;
    unsigned int index = r >> _LogWordBits;
    unsigned int min_size = index + 1;
    _grow(min_size);
    if (index > _hwm) _hwm = index;
    if (index < _lwm) _lwm = index;
    _rm_up(index) |= (uintptr_t(1) << (r & _WordBitMask));
    assert(valid_watermarks(), "post-condition");
  }

  // Remove register from mask
  void Remove(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg >= 0, "register outside mask");
    assert(reg < (int)rm_size_bits(), "register outside mask");
    unsigned int r = (unsigned int)reg;
    _rm_up(r >> _LogWordBits) &= ~(uintptr_t(1) << (r & _WordBitMask));
  }

  // OR 'rm' into 'this'
  void OR(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    _grow(rm._rm_size);
    // OR widens the live range
    if (_lwm > rm._lwm) _lwm = rm._lwm;
    if (_hwm < rm._hwm) _hwm = rm._hwm;
    // Compute OR with all words from rm
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size; i++) {
      _rm_up(i) |= rm._rm_up(i);
    }
    // If rm is smaller than us and has the all-stack flag set, we need to set
    // all bits in the gap to 1.
    if (rm.is_AllStack() && rm._rm_size < _rm_size) {
      _set_range(rm._rm_size, 0xFF, _rm_size - rm._rm_size);
      _hwm = _rm_max();
    }
    set_AllStack(is_AllStack() || rm.is_AllStack());
    assert(valid_watermarks(), "sanity");
  }

  // AND 'rm' into 'this'
  void AND(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    _grow(rm._rm_size);
    // Compute AND with all words from rm. Do not evaluate words outside the
    // current watermark range, as they are already zero and an &= would not
    // change that
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size; i++) {
      _rm_up(i) &= rm._rm_up(i);
    }
    // If rm is smaller than our high watermark and has the all-stack flag not
    // set, we need to set all bits in the gap to 0.
    if (!rm.is_AllStack() && _hwm > rm._rm_max()) {
      _set_range(rm._rm_size, 0, _hwm - rm._rm_max());
      _hwm = rm._rm_max();
    }
    // Narrow the watermarks if &rm spans a narrower range. Update after to
    // ensure non-overlapping words are zeroed out. If rm has the all-stack
    // flag set and is smaller than our high watermark, take care not to
    // incorrectly lower the high watermark according to rm.
    if (_lwm < rm._lwm) {
      _lwm = rm._lwm;
    }
    if (_hwm > rm._hwm && !(rm.is_AllStack() && _hwm > rm._rm_max())) {
      _hwm = rm._hwm;
    }
    set_AllStack(is_AllStack() && rm.is_AllStack());
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this'.
  void SUBTRACT(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    _grow(rm._rm_size);
    unsigned int hwm = MIN2(_hwm, rm._hwm);
    unsigned int lwm = MAX2(_lwm, rm._lwm);
    for (unsigned int i = lwm; i <= hwm; i++) {
      _rm_up(i) &= ~rm._rm_up(i);
    }
    // If rm is smaller than our high watermark and has the all-stack flag set,
    // we need to set all bits in the gap to 0.
    if (rm.is_AllStack() && _hwm > rm._rm_max()) {
      _set_range(rm.rm_size(), 0, _hwm - rm._rm_max());
      _hwm = rm._rm_max();
    }
    set_AllStack(is_AllStack() && !rm.is_AllStack());
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this', but ignore everything in 'rm' that does not
  // overlap with us. Supports masks of differing offsets. Ignores all_stack
  // flags and treats them as false.
  void SUBTRACT_inner(const RegMask &rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    // Various translations due to differing offsets
    int rm_index_diff = _offset - rm._offset;
    int rm_hwm_tr = (int)rm._hwm - rm_index_diff;
    int rm_lwm_tr = (int)rm._lwm - rm_index_diff;
    int rm_rm_max_tr = (int)rm._rm_max() - rm_index_diff;
    int rm_rm_size_tr = (int)rm._rm_size - rm_index_diff;
    int hwm = MIN2((int)_hwm, rm_hwm_tr);
    int lwm = MAX2((int)_lwm, rm_lwm_tr);
    for (int i = lwm; i <= hwm; i++) {
      assert(i + rm_index_diff < (int)rm._rm_size, "sanity");
      assert(i + rm_index_diff >= 0, "sanity");
      _rm_up(i) &= ~rm._rm_up(i + rm_index_diff);
    }
    assert(valid_watermarks(), "sanity");
  }

  // Roll over the register mask. The main use is to expose a new set of stack
  // slots for the register allocator.
  void rollover() {
    assert(is_AllStack_only(),"rolling over non-empty mask");
    _offset += _rm_size;
    Set_All_From_Offset();
  }

  // Compute size of register mask: number of bits
  uint Size() const;

#ifndef PRODUCT
  void print() const { dump(); }
  void dump(outputStream *st = tty) const; // Print a mask
#endif

  static const RegMask Empty;   // Common empty mask
  static const RegMask All;     // Common all mask

};

class RegMaskIterator {
 private:
  uintptr_t _current_bits;
  unsigned int _next_index;
  OptoReg::Name _reg;
  const RegMask& _rm;
 public:
  RegMaskIterator(const RegMask& rm) : _current_bits(0), _next_index(rm._lwm), _reg(OptoReg::Bad), _rm(rm) {
    // Calculate the first element
    next();
  }

  bool has_next() {
    return _reg != OptoReg::Bad;
  }

  // Get the current element and calculate the next
  OptoReg::Name next() {
    OptoReg::Name r = _reg;

    // This bit shift scheme, borrowed from IndexSetIterator,
    // shifts the _current_bits down by the number of trailing
    // zeros - which leaves the "current" bit on position zero,
    // then subtracts by 1 to clear it. This quirk avoids the
    // undefined behavior that could arise if trying to shift
    // away the bit with a single >> (next_bit + 1) shift when
    // next_bit is 31/63. It also keeps number of shifts and
    // arithmetic ops to a minimum.

    // We have previously found bits at _next_index - 1, and
    // still have some left at the same index.
    if (_current_bits != 0) {
      unsigned int next_bit = find_lowest_bit(_current_bits);
      assert(_reg != OptoReg::Bad, "can't be in a bad state");
      assert(next_bit > 0, "must be");
      assert(((_current_bits >> next_bit) & 0x1) == 1, "lowest bit must be set after shift");
      _current_bits = (_current_bits >> next_bit) - 1;
      _reg = OptoReg::add(_reg, next_bit);
      return r;
    }

    // Find the next word with bits
    while (_next_index <= _rm._hwm) {
      _current_bits = _rm._rm_up(_next_index++);
      if (_current_bits != 0) {
        // Found a word. Calculate the first register element and
        // prepare _current_bits by shifting it down and clearing
        // the lowest bit
        unsigned int next_bit = find_lowest_bit(_current_bits);
        assert(((_current_bits >> next_bit) & 0x1) == 1, "lowest bit must be set after shift");
        _current_bits = (_current_bits >> next_bit) - 1;
        _reg = OptoReg::Name(_rm.offset_bits() + ((_next_index - 1) << RegMask::_LogWordBits) + next_bit);
        return r;
      }
    }

    // No more bits
    _reg = OptoReg::Name(OptoReg::Bad);
    return r;
  }
};

// Do not use this constant directly in client code!
#undef RM_SIZE

#endif // SHARE_OPTO_REGMASK_HPP
