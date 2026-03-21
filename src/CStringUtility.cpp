#include "CStringUtility.h"


bool CStringUtility::EndsWithNoCase(const CString string, const CString suffix) {
    auto right = string.Right(suffix.GetLength());
    return (right.CompareNoCase(suffix) == 0);
}
