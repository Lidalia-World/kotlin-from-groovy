package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.testsupport.Counter

class OperatorTest {

  @Test
  fun `operator plus works`() {
    assert(Counter(3) + Counter(4) == Counter(7))
  }

  @Test
  fun `operator get works`() {
    assert(Counter(10)[5] == 15)
  }
}
