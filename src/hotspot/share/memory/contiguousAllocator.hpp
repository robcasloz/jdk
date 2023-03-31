#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "runtime/os.hpp"

#include <sys/mman.h>
#include <stdlib.h>
#include <new>

#ifndef SHARE_MEMORY_CONTIGUOUSALLOCATOR_HPP
#define SHARE_MEMORY_CONTIGUOUSALLOCATOR_HPP

// Allocates memory into a contiguous fixed-size area at page-sized granularity.
// Does not account for huge pages.
class ContiguousAllocator {
private:
  char* my_mmap() {
    // MAP_FIXED is intentionally left out, to leave existing mappings intact.
    const int flags = MAP_PRIVATE | MAP_NORESERVE | MAP_ANONYMOUS;
    char* addr = (char*)::mmap(nullptr, default_size, PROT_READ|PROT_WRITE, flags, -1, 0);
    if (addr != nullptr) {
      MemTracker::record_virtual_memory_reserve(addr, default_size, CALLER_PC, mtChunk);
    }
    return addr == MAP_FAILED ? nullptr : (char*)addr;
  }
public:
  static const size_t chunk_size = 1 << 21; // 2 MiB
  static const size_t default_size = 1*G;
  // The number of unused-but-allocated chunks that we allow before madvising() that they're not needed.
  static const size_t slack = 4;
  const size_t size;
  char* start;
  char* offset;
  char* committed_boundary;
  ContiguousAllocator(size_t size, MEMFLAGS flag):
    size(size), start(my_mmap()),
    offset(nullptr),
    committed_boundary(nullptr) {
    offset = align_up(start, 1 << 21);
    committed_boundary = offset;
    assert((uintptr_t)offset % 4*K == 0, "not aligned");
    assert((uintptr_t)offset % 64*K == 0, "not aligned");
  }

  ContiguousAllocator(MEMFLAGS flag)
    : ContiguousAllocator(default_size, flag){
  }
  ~ContiguousAllocator() {
    os::release_memory(start, size);
  }
  struct AllocationResult { void* loc; size_t sz; };
  AllocationResult alloc(size_t size) {
    size_t chunk_aligned_size = align_up(size, chunk_size);
    char* p = this->offset;
    if (p + chunk_aligned_size >= start + this->size) {
      return {nullptr, 0};
    }
    MemTracker::record_virtual_memory_commit((address)p, chunk_aligned_size, CALLER_PC);
    this->offset = (char*)(p + chunk_aligned_size);
    committed_boundary = MAX2(this->offset, this->committed_boundary);
    return {p, chunk_aligned_size};
  }
  // This is a NOP. Use reset_to(void* p) instead.
  void free(void* p) {
  }

  void reset_to(void* p) {
    void* chunk_aligned_pointer = align_up(p, chunk_size);
    offset = (char*)chunk_aligned_pointer;
    size_t unused_bytes = committed_boundary - offset;

    // We don't want to keep around too many pages that aren't in use,
    // so we ask the OS to throw away the physical backing, while keeping the memory reserved.
    if (unused_bytes > slack*chunk_size) {
      // Look into MADV_FREE/MADV_COLD
      ::madvise(offset, unused_bytes, MADV_DONTNEED);
      // The actual reserved region(s) might not cover this whole area, therefore
      // the reserved region will not be found. We must first register a covering region.
      // Here's another issue: NMT wants the flags to match, but we've got no clue.
      // Just implement a solution for this.
      //MemTracker::record_virtual_memory_reserve(offset, size, CALLER_PC);
      //MemTracker::record_virtual_memory_release(offset, size);
    }
  }
};

#endif // SHARE_MEMORY_CONTIGUOUSALLOCATOR_HPP
